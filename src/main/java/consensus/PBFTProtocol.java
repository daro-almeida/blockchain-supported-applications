package consensus;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
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

        triggerNotification(new InitializedNotification(this.self, this.key, this.view));

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ignored) {
        }

        view.forEach(node -> openConnection(node.host()));

        //Installing first view
        //triggerNotification(new ViewChange(view, viewNumber));
    }

    // --------------------------------------- Auxiliary Functions -----------------------------------

    private void commitRequests() {
        var prePrepare = prePreparesLog.get(nextToExecute);
        while (prePrepare != null && committed(view.getViewNumber(), nextToExecute)) {
            var request = prePrepare.getRequest();
            triggerNotification(new CommittedNotification(request.getBlock(), request.getSignature()));
            logger.trace("Committed request seq=" + nextToExecute + ", view=" + view.getViewNumber() + ": " + Utils.bytesToHex(request.getDigest()));
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

    // --------------------------------------- Predicates -----------------------------------

    /*
     * We define the predicate prepared(m,v,n,i) to be true if and only if replica i has inserted in its log: the
     * request m, a pre-prepare for m in view v with sequence number n, and 2f prepares from different backups that
     * match the pre-prepare.
     */
    private boolean prepared(int v, int n) {
        var prePrepare = prePreparesLog.get(n);
        var prepares = preparesLog.get(n);
        //TODO not optimal but works, fix later
        return prePrepare != null && prePrepare.getViewNumber() == v && prepares != null && prepares.size() >= 2 * f + 1 &&
                prepares.stream().filter(prepare -> prepare.getViewNumber() == v && prepare.getSeq() == n &&
                        Arrays.equals(prepare.getDigest(), prePrepare.getDigest())).count() >= 2L * f + 1;
        // 2f + 1 because it includes the pre-prepare from the primary
    }

    /*
     * true if and only if prepared(m,v,n,i) is true and has accepted 2f + 1 commits (possibly including its own) from
     *  different replicas that match the pre-prepare for m; a commit matches a pre-prepare if they have the same view,
     *  sequence number, and digest.
     */
    private boolean committed(int v, int n) {
        var commits = commitsLog.get(n);
        //TODO not optimal but works, fix later
        return commits != null && prepared(v, n) && commits.size() >= 2 * f + 1 &&
                commits.stream().filter(commit -> commit.getViewNumber() == v && commit.getSeq() == n &&
                        Arrays.equals(commit.getDigest(), prePreparesLog.get(n).getDigest())).count() >= 2L * f + 1;
    }

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
        //TODO NOW

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
        if (!validatePrepare(msg))
            return;
        if (sentCommits.contains(msg.getSeq())) return;

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
        var prePrepare = prePreparesLog.get(msg.getSeq());
        if (prePrepare != null && prepared(msg.getViewNumber(), msg.getSeq())) {
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

        if (!validateCommit(msg))
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

}
