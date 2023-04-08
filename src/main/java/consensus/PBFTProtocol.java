package consensus;

import consensus.messages.*;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import consensus.utils.PBFTPredicates;
import consensus.utils.PBFTUtils;
import consensus.utils.PreparedProof;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.MultithreadedTCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Node;
import utils.Utils;
import utils.View;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;

public class PBFTProtocol extends GenericProtocol {

    public static final String PROTO_NAME = "pbft";
    public static final short PROTO_ID = 100;
    public static final String ADDRESS_KEY = "address";
    public static final String PORT_KEY = "base_port";
    public static final String INITIAL_MEMBERSHIP_KEY = "initial_membership";
    public static final String BOOTSTRAP_PRIMARY_ID_KEY = "bootstrap_primary_id";

    private static final Logger logger = LogManager.getLogger(PBFTProtocol.class);

    private final Node self;
    private final View view;
    private final int f;

    //seq -> msg set
    private final Map<Integer, PrePrepareMessage> prePreparesLog = new HashMap<>();
    private final Map<Integer, Set<PrepareMessage>> preparesLog = new HashMap<>();
    private final Map<Integer, Set<CommitMessage>> commitsLog = new HashMap<>();
    private final Map<Integer, Set<ViewChangeMessage>> viewChangesLog = new HashMap<>();

    //seq
    private final Set<Integer> sentCommits = new HashSet<>();
    private final Set<Integer> askedViewChanges = new HashSet<>();

    private final PrivateKey key;

    private int seq = 0;
    private int nextToExecute = 0;

    public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
        super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

        var id = Integer.parseInt(props.getProperty("id"));
        var selfHost = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
                Integer.parseInt(props.getProperty(PORT_KEY)));
        var selfCryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
        try {
            var truststore = Crypto.getTruststore(props);
            this.self = new Node(id, selfHost, truststore.getCertificate(selfCryptoName).getPublicKey());
            this.key = Crypto.getPrivateKey(selfCryptoName, props);

            String[] membership = props.getProperty(INITIAL_MEMBERSHIP_KEY).split(",");
            var nodeList = new ArrayList<Node>(membership.length);
            for (int i = 1; i <= membership.length; i++) {
                var member = membership[i-1];
                var tokens = member.split(":");
                var host = new Host(InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1]));
                if (self.id() == i)
                    nodeList.add(self);
                else {
                    var cryptoName = Crypto.CRYPTO_NAME_PREFIX + i;
                    nodeList.add(new Node(i, host, truststore.getCertificate(cryptoName).getPublicKey()));
                }
            }
            var primaryId = Integer.parseInt(props.getProperty(BOOTSTRAP_PRIMARY_ID_KEY));
            this.view = new View(nodeList, nodeList.get(primaryId - 1));
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException |
                 UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }

        this.f = (view.size() - 1) / 3;
        assert this.f > 0;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        Properties peerProps = new Properties();
        peerProps.put(MultithreadedTCPChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        peerProps.setProperty(TCPChannel.PORT_KEY, props.getProperty(PORT_KEY));
        int peerChannel = createChannel(TCPChannel.NAME, peerProps);

        logger.info("Standing by to establish connections (5s)");

        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(SuspectLeader.REQUEST_ID, this::uponSuspectLeaderRequest);

        registerMessageHandler(peerChannel, PrePrepareMessage.MESSAGE_ID, this::uponPrePrepareMessage);
        registerMessageHandler(peerChannel, PrepareMessage.MESSAGE_ID, this::uponPrepareMessage);
        registerMessageHandler(peerChannel, CommitMessage.MESSAGE_ID, this::uponCommitMessage);
        registerMessageHandler(peerChannel, ViewChangeMessage.MESSAGE_ID, this::uponViewChangeMessage);
        registerMessageHandler(peerChannel, NewViewMessage.MESSAGE_ID, this::uponNewViewMessage);

        registerMessageSerializer(peerChannel, PrePrepareMessage.MESSAGE_ID, PrePrepareMessage.serializer);
        registerMessageSerializer(peerChannel, PrepareMessage.MESSAGE_ID, PrepareMessage.serializer);
        registerMessageSerializer(peerChannel, CommitMessage.MESSAGE_ID, CommitMessage.serializer);

        registerChannelEventHandler(peerChannel, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
        registerChannelEventHandler(peerChannel, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(peerChannel, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(peerChannel, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(peerChannel, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ignored) {
        }

        view.forEach(node -> openConnection(node.host()));

        triggerNotification(new InitializedNotification(peerChannel, this.self, this.key, this.view));

    }


    // --------------------------------------- Request Handlers -----------------------------------

    private void uponProposeRequest(ProposeRequest req, short sourceProto) {
        assert view.getPrimary().equals(self);

        logger.trace("Received request: " + Utils.bytesToHex(req.getDigest()));
        var prePrepareMessage = new PrePrepareMessage(view.getViewNumber(), seq, req.getDigest(), req);
        prePreparesLog.put(seq, prePrepareMessage);
        var prepareMessage = new PrepareMessage(prePrepareMessage, view.getPrimary().id());
        preparesLog.computeIfAbsent(seq, k -> new HashSet<>()).add(prepareMessage);
        Crypto.signMessage(prepareMessage, key);
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(prePrepareMessage, node.host());
        });
        seq++;
    }

    private void uponSuspectLeaderRequest(SuspectLeader req, short sourceProto) {
        if (req.getCurrentViewNumber() != view.getViewNumber() && !askedViewChanges.contains(req.getCurrentViewNumber() + 1))
            return;

        var viewChangeMessage = new ViewChangeMessage(req.getCurrentViewNumber() + 1,
                nextToExecute - 1, calculatePreparedProofs(), self.id());
        Crypto.signMessage(viewChangeMessage, key);

        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(viewChangeMessage, node.host());
        });
        askedViewChanges.add(req.getCurrentViewNumber() + 1);
    }


    // --------------------------------------- Message Handlers -----------------------------------

    private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrePrepareMessage: " + msg.getSeq());
        if (!validatePrePrepare(msg))
            return;

        prePreparesLog.put(msg.getSeq(), msg);
        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(new PrepareMessage(msg, view.getPrimary().id()));

        var prepareMessage = new PrepareMessage(msg, this.self.id());
        Crypto.signMessage(prepareMessage, key);

        view.forEach(node -> sendMessage(prepareMessage, node.host()));
        seq = Math.max(msg.getSeq() + 1, seq);
    }

    private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrepareMessage: " + msg.getSeq() + " from " + msg.getNodeId());
        if (!validatePrepare(msg) || sentCommits.contains(msg.getSeq()))
            return;

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
        var prePrepare = prePreparesLog.get(msg.getSeq());
        if (prePrepare != null && PBFTPredicates.prepared(this.f, prePrepare, preparesLog.get(msg.getSeq()))) {
            var commitMessage = new CommitMessage(msg, this.self.id());
            Crypto.signMessage(commitMessage, key);

            view.forEach(node -> {
                if (!node.equals(self))
                    sendMessage(commitMessage, node.host());
            });
            sentCommits.add(msg.getSeq());

        }
    }

    private void uponCommitMessage(CommitMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received CommitMessage: " + msg.getSeq() + " from " + msg.getNodeId());

        if (!validateCommit(msg) || msg.getSeq() < nextToExecute)
            return;

        commitsLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);

        if (msg.getSeq() == nextToExecute)
            commitRequests();
    }

    private void uponViewChangeMessage(ViewChangeMessage msg, Host sender, short sourceProtocol, int channelId) {
        if(!validateViewChangeMessage(msg))
            return;

        viewChangesLog.computeIfAbsent(msg.getNewViewNumber(), k -> new HashSet<>()).add(msg);
        var viewChanges = viewChangesLog.get(msg.getNewViewNumber());

        if (viewChanges.size() >= 2*f && view.nextLeader().equals(self)) {
            var primaryViewChange = new ViewChangeMessage(msg.getNewViewNumber(), nextToExecute - 1,
                    calculatePreparedProofs(), self.id());
            viewChanges.add(primaryViewChange);

            Set<PrePrepareMessage> prePrepares = PBFTUtils.newPrePrepareMessages(msg.getNewViewNumber(), viewChanges);
            prePrepares.forEach(prePrepare -> {
                Crypto.signMessage(prePrepare, key);

                var oldPrePrepare = prePreparesLog.get(prePrepare.getSeq());
                if (oldPrePrepare == null)
                    prePreparesLog.put(prePrepare.getSeq(), prePrepare);
                else
                    prePreparesLog.put(prePrepare.getSeq(), new PrePrepareMessage(prePrepare.getViewNumber(), prePrepare));
            });

            var newViewMessage = new NewViewMessage(msg.getNewViewNumber(), viewChanges, prePrepares);
            Crypto.signMessage(newViewMessage, key);

            view.forEach(node -> {
                if (!node.equals(self))
                    sendMessage(newViewMessage, node.host());
            });
            view.updateView(msg.getNewViewNumber(), this.self);
            triggerNotification(new ViewChange(view));
        }
    }

    private void uponNewViewMessage(NewViewMessage msg, Host sender, short sourceProtocol, int channelId) {
        if (!validateNewViewMessage(msg))
            return;

        msg.getPrePrepares().forEach(prePrepare -> {
            var oldPrePrepare = prePreparesLog.get(prePrepare.getSeq());
            if (oldPrePrepare == null)
                prePreparesLog.put(prePrepare.getSeq(), prePrepare);
            else
                prePreparesLog.put(prePrepare.getSeq(), new PrePrepareMessage(prePrepare.getViewNumber(), prePrepare));

            var prepareMessage = new PrepareMessage(prePrepare, view.getPrimary().id());
            Crypto.signMessage(prepareMessage, key);

            preparesLog.put(prePrepare.getSeq(), new HashSet<>());
            preparesLog.get(prePrepare.getSeq()).add(prepareMessage);

            view.forEach(node -> {
                if (!node.equals(self))
                    sendMessage(prepareMessage, node.host());
            });
        });

        view.updateView(msg.getNewViewNumber(), view.nextLeader());
        triggerNotification(new ViewChange(view));
    }

    // --------------------------------------- Notification Handlers ------------------------------------------

    // --------------------------------------- Timer Handlers -------------------------------------------------

    // --------------------------------------- Auxiliary Functions --------------------------------------------

    private void commitRequests() {
        var prePrepare = prePreparesLog.get(nextToExecute);
        while (PBFTPredicates.committed(this.f, prePrepare, preparesLog.get(nextToExecute), commitsLog.get(nextToExecute))) {
            var request = prePrepare.getRequest();
            triggerNotification(new CommittedNotification(request.getBlock(), request.getSignature()));
            logger.trace("Committed request seq=" + nextToExecute + ", view=" + view.getViewNumber() + ": " + Utils.bytesToHex(request.getDigest()));

            prePrepare = prePreparesLog.get(++nextToExecute);
        }
    }

    private Map<Integer, PreparedProof> calculatePreparedProofs() {
        Map<Integer, PreparedProof> preparedProofs = new HashMap<>();
        for (var prepares: preparesLog.entrySet()) {
            var seq = prepares.getKey();
            // if sent commits then must have prepared this request
            if (sentCommits.contains(seq)) {
                preparedProofs.put(seq,
                        new PreparedProof(prePreparesLog.get(seq).nullifyRequest(), prepares.getValue()));
            }
        }
        return preparedProofs;
    }



    private boolean validatePrePrepare(PrePrepareMessage msg) {
        if (msg.getViewNumber() != view.getViewNumber()) {
            logger.warn("PrePrepareMessage: Invalid view number: " + msg.getViewNumber() + " != " + view.getViewNumber());
            return false;
        }
        if (prePreparesLog.containsKey(msg.getSeq())) {
            logger.warn("PrePrepareMessage already exists: " + msg.getSeq());
            return false;
        }
        if (!Arrays.equals(msg.getDigest(), msg.getRequest().getDigest())) {
            logger.warn("PrePrepareMessage: Digests don't match: seq=" + msg.getSeq() + ": " +
                    Utils.bytesToHex(msg.getDigest()) + " != " + Utils.bytesToHex(msg.getRequest().getDigest()));
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getPrimary().publicKey())) {
            logger.warn("PrePrepareMessage: Invalid signature: " + msg.getSeq());
            return false;
        }
        return true;
    }

    private boolean validatePrepare(PrepareMessage msg) {
        if (msg.getViewNumber() != view.getViewNumber()) {
            logger.warn("PrepareMessage: Invalid view number: " + msg.getViewNumber() + " != " + view.getViewNumber());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PrepareMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateCommit(CommitMessage msg) {
        if (msg.getViewNumber() != view.getViewNumber()) {
            logger.warn("CommitMessage: Invalid view number: " + msg.getViewNumber() + " != " + view.getViewNumber());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("CommitMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateViewChangeMessage(ViewChangeMessage msg) {
        if (msg.getNewViewNumber() <= view.getViewNumber()) {
            logger.warn("ViewChangeMessage: Invalid view number: " + msg.getNewViewNumber() + " <= " + view.getViewNumber());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("ViewChangeMessage: Invalid signature: " + msg.getNewViewNumber() + ", " + msg.getNodeId());
            return false;
        }
        if (!msg.preparedProofsValid(f, view.publicKeys())) {
            logger.warn("ViewChangeMessage: Invalid prepared proofs: " + msg.getNewViewNumber() + ", " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateNewViewMessage(NewViewMessage msg) {
        if (msg.getNewViewNumber() <= view.getViewNumber()) {
            logger.warn("NewViewMessage: Invalid view number: " + msg.getNewViewNumber() + " <= " + view.getViewNumber());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getPrimary().publicKey())) {
            logger.warn("NewViewMessage: Invalid signature: " + view.getPrimary().id());
            return false;
        }
        if (!msg.viewChangesValid(f, view.publicKeys())) {
            logger.warn("NewViewMessage: Invalid view change messages: " + msg.getNewViewNumber());
            return false;
        }
        if (!msg.prePreparesValid(view.getPrimary().publicKey())) {
            logger.warn("NewViewMessage: Invalid pre-prepare messages: " + msg.getNewViewNumber());
            return false;
        }
        return true;
    }

    // --------------------------------------- Connection Manager Functions -----------------------------------

    private void uponOutConnectionUp(OutConnectionUp event, int channel) {
        logger.debug(event);
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channel) {
        logger.warn(event);
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> ev, int ch) {
        logger.warn(ev);
        openConnection(ev.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channel) {
        logger.debug(event);
    }

    private void uponInConnectionDown(InConnectionDown event, int channel) {
        logger.warn(event);
    }


}
