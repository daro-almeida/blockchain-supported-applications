package consensus;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import consensus.notifications.CommittedNotification;
import consensus.requests.ProposeRequest;
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
import utils.Utils;

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

    private static final Logger logger = LogManager.getLogger(PBFTProtocol.class);
    // cryptoName -> public key
    private final Map<String, PublicKey> publicKeys;
    private final Host self;
    private final List<Host> view;
    private final int f;
    private final Map<Integer, PrePrepareMessage> prePreparesLog;
    private final Map<Integer, Set<PrepareMessage>> preparesLog;
    // seq -> commit set
    private final Map<Integer, Set<CommitMessage>> commitsLog;
    private final Set<Integer> sentCommits;
    private String cryptoName;
    private PrivateKey key;
    private int viewNumber;
    private int seq;
    private int nextToExecute;
    private String primaryCryptoName;

    public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
        super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

        this.publicKeys = new HashMap<>();

        this.seq = 0;
        this.nextToExecute = 0;
        this.viewNumber = 0;
        this.primaryCryptoName = props.getProperty("bootstrap_primary");
        this.prePreparesLog = new HashMap<>();
        this.preparesLog = new HashMap<>();
        this.commitsLog = new HashMap<>();
        this.sentCommits = new HashSet<>();

        this.self = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
                Integer.parseInt(props.getProperty(PORT_KEY)));

        this.view = new LinkedList<>();
        String[] membership = props.getProperty(INITIAL_MEMBERSHIP_KEY).split(",");
        for (String s : membership) {
            String[] tokens = s.split(":");
            view.add(new Host(InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1])));
        }

        this.f = (view.size() - 1) / 3;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        try {
            cryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
            KeyStore truststore = Crypto.getTruststore(props);
            key = Crypto.getPrivateKey(cryptoName, props);
            for (var it = truststore.aliases().asIterator(); it.hasNext(); ) {
                var name = it.next();
                publicKeys.put(name, truststore.getCertificate(name).getPublicKey());
            }

        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
                 | IOException e) {
            throw new RuntimeException(e);
        }

        Properties peerProps = new Properties();
        peerProps.put(MultithreadedTCPChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        peerProps.setProperty(TCPChannel.PORT_KEY, props.getProperty(PORT_KEY));
        int peerChannel = createChannel(TCPChannel.NAME, peerProps);

        logger.info("Standing by to establish connections (5s)");

        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);

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

        view.forEach(this::openConnection);

        //Installing first view
        //triggerNotification(new ViewChange(view, viewNumber));
    }

     // --------------------------------------- Auxiliary Functions -----------------------------------

    private void commitRequests() {
        var prePrepare = prePreparesLog.get(nextToExecute);
        while (prePrepare != null && committed(viewNumber, nextToExecute)) {
            var request = prePrepare.getRequest();
            triggerNotification(new CommittedNotification(request.getBlock(), request.getSignature()));
            logger.info("Committed request seq=" + nextToExecute + ", view=" + viewNumber + ": " + Utils.bytesToHex(request.getDigest()));
            prePrepare = prePreparesLog.get(++nextToExecute);
        }
    }

    private boolean validatePrePrepare(PrePrepareMessage msg) {
        try {
            if (msg.getViewNumber() != viewNumber) {
                logger.warn("PrePrepareMessage: Invalid view number: " + msg.getViewNumber() + " != " + viewNumber);
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
            if (!msg.checkSignature(publicKeys.get(primaryCryptoName))) {
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
            if (msg.getViewNumber() != viewNumber) {
                logger.warn("PrepareMessage: Invalid view number: " + msg.getViewNumber() + " != " + viewNumber);
                return false;
            }
            if (!msg.checkSignature(publicKeys.get(msg.getCryptoName()))) {
                logger.warn("PrepareMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getCryptoName());
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
            if (msg.getViewNumber() != viewNumber) {
                logger.warn("CommitMessage: Invalid view number: " + msg.getViewNumber() + " != " + viewNumber);
                return false;
            }
            if (!msg.checkSignature(publicKeys.get(msg.getCryptoName()))) {
                logger.warn("CommitMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getCryptoName());
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
        return  commits != null && prepared(v, n) && commits.size() >= 2 * f + 1 &&
                commits.stream().filter(commit -> commit.getViewNumber() == v && commit.getSeq() == n &&
                        Arrays.equals(commit.getDigest(), prePreparesLog.get(n).getDigest())).count() >= 2L * f + 1;
    }

     // --------------------------------------- Request Handlers -----------------------------------

    private void uponProposeRequest(ProposeRequest req, short sourceProto) {
        assert cryptoName.equals(primaryCryptoName);

        logger.info("Received request: " + Utils.bytesToHex(req.getDigest()));
        var prePrepareMessage = new PrePrepareMessage(viewNumber, seq, req.getDigest(), req);
        prePreparesLog.put(seq, prePrepareMessage);
        var prepareMessage = new PrepareMessage(prePrepareMessage, primaryCryptoName);
        preparesLog.computeIfAbsent(seq, k -> new HashSet<>()).add(prepareMessage);
        try {
            prePrepareMessage.signMessage(key);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            throw new RuntimeException(e);
        }
        view.forEach(h -> {
            if (!h.equals(self))
                sendMessage(prePrepareMessage, h);
        });
        seq++;
    }

    // --------------------------------------- Message Handlers -----------------------------------

    private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.info("Received PrePrepareMessage: " + msg.getSeq());
        if (!validatePrePrepare(msg))
            return;

        prePreparesLog.put(msg.getSeq(), msg);
        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(new PrepareMessage(msg, primaryCryptoName));

        var prepareMessage = new PrepareMessage(msg, cryptoName);
        try {
            prepareMessage.signMessage(key);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
            throw new RuntimeException(e);
        }
        view.forEach(h -> sendMessage(prepareMessage, h));
        seq = Math.max(msg.getSeq() + 1, seq);
    }

    private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.info("Received PrepareMessage: " + msg.getSeq() + " from " + msg.getCryptoName());
        if (!validatePrepare(msg))
            return;

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);

        if (sentCommits.contains(msg.getSeq())) return;
        var prePrepare = prePreparesLog.get(msg.getSeq());
        if (prePrepare != null && prepared(msg.getViewNumber(), msg.getSeq())) {
            var commitMessage = new CommitMessage(msg, cryptoName);
            try {
                commitMessage.signMessage(key);
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException |
                     InvalidSerializerException e) {
                throw new RuntimeException(e);
            }
            view.forEach(h -> sendMessage(commitMessage, h));
            sentCommits.add(msg.getSeq());
        }
    }

    private void uponCommitMessage(CommitMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.info("Received CommitMessage: " + msg.getSeq() + " from " + msg.getCryptoName());

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
