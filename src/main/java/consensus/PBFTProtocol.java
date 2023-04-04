package consensus;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import consensus.utils.PBFTPredicates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidFormatException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.babel.generic.signed.NoSignaturePresentException;
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
    private final Map<Integer, PrePrepareMessage> prePreparesLog;
    private final Map<Integer, Set<PrepareMessage>> preparesLog;
    private final Map<Integer, Set<CommitMessage>> commitsLog;
    //seq
    private final Set<Integer> sentCommits;
    private final PrivateKey key;

    private int seq;
    private int nextToExecute;

    public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
        super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

        this.seq = 0;
        this.nextToExecute = 0;

        this.prePreparesLog = new HashMap<>();
        this.preparesLog = new HashMap<>();
        this.commitsLog = new HashMap<>();
        this.sentCommits = new HashSet<>();

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


    // --------------------------------------- Predicates -----------------------------------



    // --------------------------------------- Request Handlers -----------------------------------

    private void uponProposeRequest(ProposeRequest req, short sourceProto) {
        assert view.getPrimary().equals(self);

        logger.trace("Received request: " + Utils.bytesToHex(req.getDigest()));
        var prePrepareMessage = new PrePrepareMessage(view.getViewNumber(), seq, req.getDigest(), req);
        prePreparesLog.put(seq, prePrepareMessage);
        var prepareMessage = new PrepareMessage(prePrepareMessage, view.getPrimary().id());
        preparesLog.computeIfAbsent(seq, k -> new HashSet<>()).add(prepareMessage);
        try {
            prePrepareMessage.signMessage(key);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            throw new RuntimeException(e);
        }
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(prePrepareMessage, node.host());
        });
        seq++;
    }

    private void uponSuspectLeaderRequest(SuspectLeader req, short sourceProto) {

        //TODO not sure in the order or correctness of this:
        // 1. send ViewChangeMessage (not made yet) to all other replicas
        // 2. take care of pending operations of current view so they're not discarded (thought in lecture) (might be
        // done when receiving ViewChangeMessage)
        // 3. calculate new leader (next in id order something like (id = prevId + 1 % view.size)), update view, then
        // send ViewChanged to blockchain (might also not be this method that does this)

        // for now doing just 3. is easy and good enough :)
    }


    // --------------------------------------- Message Handlers -----------------------------------

    private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrePrepareMessage: " + msg.getSeq());
        if (!validatePrePrepare(msg))
            return;

        prePreparesLog.put(msg.getSeq(), msg);
        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(new PrepareMessage(msg, view.getPrimary().id()));

        var prepareMessage = new PrepareMessage(msg, this.self.id());
        try {
            prepareMessage.signMessage(key);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
            throw new RuntimeException(e);
        }
        view.forEach(node -> sendMessage(prepareMessage, node.host()));
        seq = Math.max(msg.getSeq() + 1, seq);
    }

    private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrepareMessage: " + msg.getSeq() + " from " + msg.getNodeId());
        if (!validatePrepare(msg) || sentCommits.contains(msg.getSeq()))
            return;

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
        var prePrepare = prePreparesLog.get(msg.getSeq());
        if (prePrepare != null && PBFTPredicates.prepared(msg.getViewNumber(), msg.getSeq(), this.f,
                prePrepare, preparesLog.get(msg.getSeq()))) {
            var commitMessage = new CommitMessage(msg, this.self.id());
            try {
                commitMessage.signMessage(key);
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException |
                     InvalidSerializerException e) {
                throw new RuntimeException(e);
            }
            view.forEach(node -> sendMessage(commitMessage, node.host()));
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

    // --------------------------------------- Notification Handlers ------------------------------------------

    // --------------------------------------- Timer Handlers -------------------------------------------------

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

    // --------------------------------------- Auxiliary Functions -----------------------------------

    private void commitRequests() {
        var prePrepare = prePreparesLog.get(nextToExecute);
        while (prePrepare != null && PBFTPredicates.committed(view.getViewNumber(), nextToExecute, this.f,
                prePrepare, preparesLog.get(nextToExecute), commitsLog.get(nextToExecute))) {
            var request = prePrepare.getRequest();
            triggerNotification(new CommittedNotification(request.getBlock(), request.getSignature()));
            logger.trace("Committed request seq=" + nextToExecute + ", view=" + view.getViewNumber() + ": " + Utils.bytesToHex(request.getDigest()));

            // removing messages related to this commit from logs, still don't know for now if we might need them later
            prePreparesLog.remove(nextToExecute);
            //preparesLog.remove(nextToExecute);
            commitsLog.remove(nextToExecute);

            prePrepare = prePreparesLog.get(++nextToExecute);
        }
    }

    private boolean validatePrePrepare(PrePrepareMessage msg) {
        try {
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
            if (!msg.checkSignature(view.getPrimary().publicKey())) {
                logger.warn("PrePrepareMessage: Invalid signature: " + msg.getSeq());
                return false;
            }
        } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
            logger.warn(e.getMessage());
            return false;
        }
        return true;
    }

    private boolean validatePrepare(PrepareMessage msg) {
        try {
            if (msg.getViewNumber() != view.getViewNumber()) {
                logger.warn("PrepareMessage: Invalid view number: " + msg.getViewNumber() + " != " + view.getViewNumber());
                return false;
            }
            if (!msg.checkSignature(view.getNode(msg.getNodeId()).publicKey())) {
                logger.warn("PrepareMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
                return false;
            }
        } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException | InvalidFormatException |
                 NoSignaturePresentException e) {
            logger.warn(e.getMessage());
            return false;
        }
        return true;
    }

    private boolean validateCommit(CommitMessage msg) {
        try {
            if (msg.getViewNumber() != view.getViewNumber()) {
                logger.warn("CommitMessage: Invalid view number: " + msg.getViewNumber() + " != " + view.getViewNumber());
                return false;
            }
            if (!msg.checkSignature(view.getNode(msg.getNodeId()).publicKey())) {
                logger.warn("CommitMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
                return false;
            }
        } catch (SignatureException | InvalidFormatException | NoSignaturePresentException | NoSuchAlgorithmException
                 | InvalidKeyException e) {
            logger.warn(e.getMessage());
            return false;
        }
        return true;
    }
}
