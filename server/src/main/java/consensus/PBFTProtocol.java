package consensus;

import blockchain.BlockChainProtocol;
import blockchain.requests.BlockReply;
import blockchain.requests.BlockRequest;
import blockchain.requests.HashReply;
import blockchain.requests.HashRequest;
import consensus.messages.*;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.MultithreadedTCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.stream.Collectors;

public class PBFTProtocol extends GenericProtocol {

    public static final String PROTO_NAME = "pbft";
    public static final short PROTO_ID = 100;
    public static final String ADDRESS_KEY = "address";
    public static final String PORT_KEY = "server_server_port";
    public static final String INITIAL_MEMBERSHIP_KEY = "initial_membership";
    public static final String BOOTSTRAP_PRIMARY_ID_KEY = "bootstrap_primary_id";

    private static final Logger logger = LogManager.getLogger(PBFTProtocol.class);

    private final Node self;
    private final View view;
    private final ViewChangeManager viewChangeManager;
    private final int f;

    //seq
    private final Map<Integer, PrePrepareMessage> prePreparesLog = new HashMap<>();
    private final Map<Integer, Set<PrepareMessage>> preparesLog = new HashMap<>();
    private final Map<Integer, Set<CommitMessage>> commitsLog = new HashMap<>();
    private final Map<Integer, Map<Node, byte[]>> receivedHashes = new HashMap<>();
    //seq, view
    private final Map<Integer, Set<Integer>> sentCommits = new HashMap<>();

    private final PrivateKey key;

    private int seq = 1;
    private int nextToExecute = 1;

    public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
        super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

        var id = Integer.parseInt(props.getProperty("id"));
        var selfHost = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
                Integer.parseInt(props.getProperty(PORT_KEY)));
        var selfCryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);

        //setup membership and keys
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
                    var node = new Node(i, host, truststore.getCertificate(cryptoName).getPublicKey());
                    nodeList.add(node);
                }
            }
            var primaryId = Integer.parseInt(props.getProperty(BOOTSTRAP_PRIMARY_ID_KEY));
            this.view = new View(nodeList, nodeList.get(primaryId - 1));
            this.viewChangeManager = new ViewChangeManager(this.self, this.view, this.key);
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

        registerReplyHandler(BlockReply.REPLY_ID, this::uponBlockReply);
        registerReplyHandler(HashReply.REPLY_ID, this::uponHashReply);

        registerMessageHandler(peerChannel, PrePrepareMessage.MESSAGE_ID, this::uponPrePrepareMessage);
        registerMessageHandler(peerChannel, PrepareMessage.MESSAGE_ID, this::uponPrepareMessage);
        registerMessageHandler(peerChannel, CommitMessage.MESSAGE_ID, this::uponCommitMessage);
        registerMessageHandler(peerChannel, ViewChangeMessage.MESSAGE_ID, this::uponViewChangeMessage);
        registerMessageHandler(peerChannel, NewViewMessage.MESSAGE_ID, this::uponNewViewMessage);
        registerMessageHandler(peerChannel, PullRequestsMessage.MESSAGE_ID, this::uponPullRequestsMessage);
        registerMessageHandler(peerChannel, PullRequestsReplyMessage.MESSAGE_ID, this::uponPullRequestsReplyMessage);
        registerMessageHandler(peerChannel, PullHashesMessage.MESSAGE_ID, this::uponPullHashMessage);
        registerMessageHandler(peerChannel, PullHashesReplyMessage.MESSAGE_ID, this::uponPullHashReplyMessage);

        registerMessageSerializer(peerChannel, PrePrepareMessage.MESSAGE_ID, PrePrepareMessage.serializer);
        registerMessageSerializer(peerChannel, PrepareMessage.MESSAGE_ID, PrepareMessage.serializer);
        registerMessageSerializer(peerChannel, CommitMessage.MESSAGE_ID, CommitMessage.serializer);
        registerMessageSerializer(peerChannel, ViewChangeMessage.MESSAGE_ID, ViewChangeMessage.serializer);
        registerMessageSerializer(peerChannel, NewViewMessage.MESSAGE_ID, NewViewMessage.serializer);
        registerMessageSerializer(peerChannel, PullRequestsMessage.MESSAGE_ID, PullRequestsMessage.serializer);
        registerMessageSerializer(peerChannel, PullRequestsReplyMessage.MESSAGE_ID, PullRequestsReplyMessage.serializer);
        registerMessageSerializer(peerChannel, PullHashesMessage.MESSAGE_ID, PullHashesMessage.serializer);
        registerMessageSerializer(peerChannel, PullHashesReplyMessage.MESSAGE_ID, PullHashesReplyMessage.serializer);

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

        Crypto.signMessage(prePrepareMessage, key);
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(prePrepareMessage, node.host());
        });
        seq++;
    }

    private void uponSuspectLeaderRequest(SuspectLeader req, short sourceProto) {
        assert req.getCurrentView() <= view.getViewNumber();

        if (viewChangeManager.canSendViewChange(req))
            return;

        int newViewNumber = viewChangeManager.newViewNumber();

        CommittedProof committedProof;
        var prePrepare = prePreparesLog.get(nextToExecute - 1);
        if (prePrepare == null) {
            committedProof = null;
        } else {
            committedProof = new CommittedProof(prePreparesLog.get(nextToExecute - 1).nullRequestPrePrepare(),
                    preparesLog.get(nextToExecute - 1), commitsLog.get(nextToExecute - 1));
        }

        var viewChangeMessage = new ViewChangeMessage(newViewNumber, nextToExecute - 1, committedProof,
                calculatePreparedProofs(), self.id());
        Crypto.signMessage(viewChangeMessage, key);

        logger.debug("Sending ViewChangeMessage: " + viewChangeMessage.getNewViewNumber());

        processViewChangeMessage(viewChangeMessage);
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(viewChangeMessage, node.host());
        });
    }

    private void uponBlockReply(BlockReply reply, short sourceProto) {
        assert !reply.getBlocksWithSignatures().isEmpty();

        logger.debug("Replying to node{} blocks: {}", reply.getRequesterId(), reply.getBlocksWithSignatures().keySet());
        var replyMsg = new PullRequestsReplyMessage(reply.getBlocksWithSignatures(), self.id());
        Crypto.signMessage(replyMsg, key);
        sendMessage(replyMsg, view.getNode(reply.getRequesterId()).host());
    }

    private void uponHashReply(HashReply reply, short sourceProto) {
        assert !reply.getHashes().isEmpty();

        logger.debug("Replying to node{} hashes: {}", reply.getRequesterId(), reply.getHashes().keySet());
        var replyMsg = new PullHashesReplyMessage(reply.getHashes(), self.id());
        Crypto.signMessage(replyMsg, key);
        sendMessage(replyMsg, view.getNode(reply.getRequesterId()).host());
    }


    // --------------------------------------- Message Handlers -----------------------------------

    private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrePrepareMessage: " + msg.getSeq());
        if (!validatePrePrepare(msg))
            return;

        prePreparesLog.put(msg.getSeq(), msg);

        var prepareMessage = new PrepareMessage(msg, this.self.id());
        Crypto.signMessage(prepareMessage, key);

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(prepareMessage);
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(prepareMessage, node.host());
        });
        seq = Math.max(seq, msg.getSeq() + 1);
    }

    private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received PrepareMessage: " + msg.getSeq() + " from " + msg.getNodeId());
        boolean alreadySentCommits = sentCommits.containsKey(msg.getSeq()) &&
                sentCommits.get(msg.getSeq()).contains(msg.getViewNumber());
        if (!validatePrepare(msg) || alreadySentCommits)
            return;

        preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
        var prePrepare = prePreparesLog.get(msg.getSeq());
        if (PBFTPredicates.prepared(this.f, prePrepare, preparesLog.get(msg.getSeq()))) {
            var commitMessage = new CommitMessage(msg, this.self.id());
            Crypto.signMessage(commitMessage, key);

            commitsLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(commitMessage);
            view.forEach(node -> {
                if (!node.equals(self))
                    sendMessage(commitMessage, node.host());
            });
            sentCommits.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg.getViewNumber());

            commitRequests();
        }
    }

    private void uponCommitMessage(CommitMessage msg, Host sender, short sourceProtocol, int channelId) {
        logger.trace("Received CommitMessage: " + msg.getSeq() + " from " + msg.getNodeId());

        if (!validateCommit(msg) || msg.getSeq() < nextToExecute)
            return;

        commitsLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);

        commitRequests();
    }

    private void uponViewChangeMessage(ViewChangeMessage msg, Host sender, short sourceProtocol, int channelId) {
        if(!validateViewChange(msg))
            return;

        processViewChangeMessage(msg);
    }

    private void uponNewViewMessage(NewViewMessage msg, Host sender, short sourceProtocol, int channelId) {
        if (!validateNewView(msg))
            return;

        processNewViewMessage(msg);
    }

    private void uponPullRequestsMessage(PullRequestsMessage msg, Host host, short sourceProtocol, int channelId) {
        if (!validatePullRequests(msg))
            return;

        sendRequest(new BlockRequest(msg.getNeededRequests(), msg.getNodeId()), BlockChainProtocol.PROTO_ID);
    }

    private void uponPullRequestsReplyMessage(PullRequestsReplyMessage msg, Host host, short sourceProtocol, int channelId) {
        if (!validatePullRequestsReply(msg))
            return;

        logger.debug("Received from node{} requests: {}", msg.getNodeId(), msg.getRequests().keySet());
        msg.getRequests().forEach((seq, request) -> {
            var prePrepare = prePreparesLog.get(seq);
            if (prePrepare == null && requestConfirmedValid(request)) {
                var newPrePrepare = new PrePrepareMessage(view.getViewNumber(), seq, request.getDigest(), request);
                // don't need to sign this prePrepare since it won't be shared
                prePreparesLog.put(seq, newPrePrepare);
                receivedHashes.remove(seq);
            } else if (prePrepare != null && Arrays.equals(prePrepare.getDigest(), request.getDigest())) {
                prePrepare.setRequest(request);
            } else {
                logger.error("Received invalid request from node{}: {}", msg.getNodeId(), seq);
            }
        });

        commitRequests();
    }

    private void uponPullHashMessage(PullHashesMessage msg, Host host, short sourceProtocol, int channelId) {
        if (!validatePullHashes(msg))
            return;

        sendRequest(new HashRequest(msg.getNeededHashes(), msg.getNodeId()), BlockChainProtocol.PROTO_ID);
    }

    private void uponPullHashReplyMessage(PullHashesReplyMessage msg, Host host, short sourceProtocol, int channelId) {
        if (!validatePullHashesReply(msg))
            return;

        logger.debug("Received from node{} hashes: {}", msg.getNodeId(), msg.getHashes().keySet());
        msg.getHashes().forEach((seq, hash) -> {
            if (receivedHashes.containsKey(seq)) {
                receivedHashes.get(seq).put(view.getNode(msg.getNodeId()), hash);
            } else //didn't ask for this or got enough
                return;

            var prePrepare = prePreparesLog.get(seq);
            if (prePrepare != null && prePrepare.getRequest() != null && requestConfirmedValid(prePrepare.getRequest())) {
                var newPrePrepare = new PrePrepareMessage(view.getViewNumber(), seq,
                        prePrepare.getRequest().getDigest(), prePrepare.getRequest());
                // don't need to sign this prePrepare since it won't be shared
                prePreparesLog.put(seq, newPrePrepare);
                receivedHashes.remove(seq);
            }
        });

        commitRequests();
    }

    // --------------------------------------- Notification Handlers ------------------------------------------

    // --------------------------------------- Timer Handlers -------------------------------------------------

    // --------------------------------------- Auxiliary Functions --------------------------------------------

    private void commitRequests() {
        var prePrepare = prePreparesLog.get(nextToExecute);
        while (PBFTPredicates.committed(this.f, prePrepare, preparesLog.get(nextToExecute), commitsLog.get(nextToExecute))) {
            var request = prePrepare.getRequest();
            if (prePrepare.getDigest() != null) { // null digests = no-op
                if (request == null)
                    return; // need to wait for the request to arrive

                triggerNotification(new CommittedNotification(prePrepare.getSeq(), request.getBlock(), request.getSignature()));
                logger.trace("Committed request seq=" + nextToExecute + ", view=" + view.getViewNumber() + ": " + Utils.bytesToHex(request.getDigest()));
            }
            if (viewChangeManager.shouldChangeView(nextToExecute))
                changeView();


            prePrepare = prePreparesLog.get(++nextToExecute);

            //keep one of each of last executed to be able to generate committed proof for view change if needed
            prePreparesLog.remove(nextToExecute - 2);
            preparesLog.remove(nextToExecute - 2);
            commitsLog.remove(nextToExecute - 2);
        }
    }

    private void changeView() {
        Integer newViewNumber = viewChangeManager.newView();
        assert newViewNumber != null && newViewNumber > view.getViewNumber();
        view.updateView(newViewNumber);
        triggerNotification(new ViewChange(view));
    }

    private void changeView(int newViewNumber) {
        assert newViewNumber > view.getViewNumber();
        viewChangeManager.newView(newViewNumber);
        view.updateView(newViewNumber);
        triggerNotification(new ViewChange(view));
    }

    private void processViewChangeMessage(ViewChangeMessage msg) {
        logger.debug("Received ViewChangeMessage: " + msg.getNewViewNumber() + " from node" + msg.getNodeId());
        var newViewMessage = viewChangeManager.processViewChangeMessage(msg, f);
        if (newViewMessage == null)
            return;
        Crypto.signMessage(newViewMessage, key);

        logger.debug("Sending NewViewMessage: " + newViewMessage.getNewViewNumber() + " to all other nodes");
        processNewViewMessage(newViewMessage);
        view.forEach(node -> {
            if (!node.equals(self))
                sendMessage(newViewMessage, node.host());
        });
    }

    private void processNewViewMessage(NewViewMessage msg) {
        var nextLeader = view.leaderInView(msg.getNewViewNumber());

        logger.debug("Received NewViewMessage: " + msg.getNewViewNumber() + " from node" + nextLeader.id());

        msg.getPrePrepares().forEach(prePrepare -> {
            var oldPrePrepare = prePreparesLog.get(prePrepare.getSeq());
            if (oldPrePrepare != null)
                prePrepare.setRequest(oldPrePrepare.getRequest());
            prePreparesLog.put(prePrepare.getSeq(), prePrepare);

            preparesLog.put(prePrepare.getSeq(), new HashSet<>());
            commitsLog.put(prePrepare.getSeq(), new HashSet<>());

            if (!nextLeader.equals(self)) {
                var prepareMessage = new PrepareMessage(prePrepare, self.id());
                Crypto.signMessage(prepareMessage, key);
                preparesLog.get(prePrepare.getSeq()).add(prepareMessage);
                view.forEach(node -> {
                    if (!node.equals(self))
                        sendMessage(prepareMessage, node.host());
                });
            }
        });

        var maxSeq = ViewChangeManager.maxSeq(msg.getViewChanges());

        pullNeededRequests(msg, maxSeq);

        if (this.nextToExecute > maxSeq) {
            changeView(msg.getNewViewNumber());
        } else {
            //need this line to accept prepares and commits for pending view change since not changing view
            viewChangeManager.setPendingViewChange(msg.getNewViewNumber());
            viewChangeManager.setChangeViewAfterCommit(maxSeq);
        }

        seq = maxSeq + 1;
    }

    private void pullNeededRequests(NewViewMessage msg, int maxSeq) {
        var neededHashes = new HashSet<Integer>();
        var neededRequests = new LinkedList<Integer>();
        for (int i = nextToExecute; i <= maxSeq; i++) {
            if (prePreparesLog.get(i) == null) {
                // if don't have pre-prepare, need f (?) hashes from other replicas to prove reply is legit
                neededRequests.add(i);
                neededHashes.add(i);
            } else if (prePreparesLog.get(i).getRequest() == null) {
                // if have pre-prepare but don't have request then just need the request (can compare to digest later)
                neededRequests.add(i);
            }
        }
        if (neededRequests.size() == 0)
            return;

        var requestsInNodes = requestsInNodes(msg);
        var requestsToPullPerNode = requestsToPullPerNode(requestsInNodes, neededRequests);

        requestsToPullPerNode.forEach((node, requests) -> {
            logger.debug("Requesting " + requests.size() + " requests from node" + node.id());
            var pullRequestsMessage = new PullRequestsMessage(requests, this.self.id());
            Crypto.signMessage(pullRequestsMessage, key);
            sendMessage(pullRequestsMessage, node.host());
        });

        requestsInNodes.forEach((node, pair) -> {
            var lastExecuted = pair.getLeft();
            var neededHashesForNode = neededHashes.stream()
                    .filter(seq -> seq <= lastExecuted)
                    .collect(Collectors.toSet());
            neededHashesForNode.forEach(seq -> receivedHashes.put(seq, new HashMap<>()));
            logger.debug("Requesting " + neededHashesForNode.size() + " hashes from node" + node.id());
            var pullHashMessage = new PullHashesMessage(neededHashesForNode, this.self.id());
            Crypto.signMessage(pullHashMessage, key);
            sendMessage(pullHashMessage, node.host());
        });
    }

    // first element of pair is seq of last executed operation of each node, second is seqs they prepared
    private Map<Node, Pair<Integer, Set<Integer>>> requestsInNodes(NewViewMessage msg) {
        var requestsInNodes = new HashMap<Node, Pair<Integer, Set<Integer>>>();
        for (var viewChange: msg.getViewChanges()) {
            var node = view.getNode(viewChange.getNodeId());
            if (!node.equals(self)) {
                var lastExecuted = viewChange.getLastExecuted();
                var prepared = new HashSet<>(viewChange.getPreparedProofs().keySet());
                requestsInNodes.put(node, Pair.of(lastExecuted, prepared));
            }
        }
        return requestsInNodes;
    }

    //TODO can optimize this to pull requests not from one replica only (minor optimization)
    private Map<Node, Set<Integer>> requestsToPullPerNode(Map<Node, Pair<Integer, Set<Integer>>> requestsInNodes, List<Integer> neededRequests) {
        var requestsToPullPerNode = new HashMap<Node, Set<Integer>>();

        for (var entry: requestsInNodes.entrySet()) {
            var node = entry.getKey();
            var lastExecuted = entry.getValue().getLeft();
            var prepared = entry.getValue().getRight();

            var requestsToPull = neededRequests.stream().filter(seq -> seq <= lastExecuted || prepared.contains(seq))
                    .collect(Collectors.toCollection(HashSet::new));
            if (requestsToPull.size() > 0) {
                requestsToPullPerNode.put(node, requestsToPull);
                neededRequests.removeAll(requestsToPull);
                if (neededRequests.size() == 0)
                    break;
            }
        }

        return requestsToPullPerNode;
    }

    private boolean requestConfirmedValid(ProposeRequest request) {
        var receivedHashesOfRequest = receivedHashes.get(seq);
        if (receivedHashesOfRequest == null || receivedHashesOfRequest.size() < f + 1)
            return false;

        int matchingDigests = 0;
        var requestHash = request.getDigest();
        for (var hash : receivedHashesOfRequest.values()) {
            if (Arrays.equals(requestHash, hash)) {
                matchingDigests++;
                if (matchingDigests >= f + 1)
                    return true;
            }
        }
        return false;
    }

    private Map<Integer, PreparedProof> calculatePreparedProofs() {
        Map<Integer, PreparedProof> preparedProofs = new HashMap<>();
        for (var prepares: preparesLog.entrySet()) {
            var seq = prepares.getKey();
            // if sent commits then must have prepared this request
            if (sentCommits.get(seq) != null && sentCommits.get(seq).contains(view.getViewNumber())) {
                preparedProofs.put(seq, new PreparedProof(prePreparesLog.get(seq).nullRequestPrePrepare(), prepares.getValue()));
            }
        }
        return preparedProofs;
    }

    private boolean validatePrePrepare(PrePrepareMessage msg) {
        if (viewChangeManager.rejectMessage(msg.getViewNumber())) {
            logger.warn("PrePrepareMessage: rejected message with invalid view number: " + msg.getViewNumber() + ", " + msg.getSeq());
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
        if (!SignaturesHelper.checkSignature(msg.getRequest().getBlock(), msg.getRequest().getSignature(), view.getPrimary().publicKey())) {
            logger.warn("PrePrepareMessage: Invalid request signature: " + msg.getSeq());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getPrimary().publicKey())) {
            logger.warn("PrePrepareMessage: Invalid signature: " + msg.getSeq());
            return false;
        }
        return true;
    }

    private boolean validatePrepare(PrepareMessage msg) {
        if (viewChangeManager.rejectMessage(msg.getViewNumber())) {
            logger.warn("PrepareMessage: rejected message with invalid view number: " + msg.getViewNumber() + ", " + msg.getSeq());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PrepareMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateCommit(CommitMessage msg) {
        if (viewChangeManager.rejectMessage(msg.getViewNumber())) {
            logger.warn("CommitMessage: rejected message with invalid view number: " + msg.getViewNumber() + ", " + msg.getSeq());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("CommitMessage: Invalid signature: " + msg.getSeq() + ", " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateViewChange(ViewChangeMessage msg) {
        if (msg.getNewViewNumber() <= view.getViewNumber()) {
            logger.warn("ViewChangeMessage: Invalid view number: {} <= {} from node{}", msg.getNewViewNumber(), view.getViewNumber(), msg.getNodeId());
            return false;
        }
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("ViewChangeMessage: Invalid signature: " + msg.getNewViewNumber() + ", node" + msg.getNodeId());
            return false;
        }
        if (!msg.isValid(f, view.publicKeys())) {
            logger.warn("ViewChangeMessage: Invalid view change: " + msg.getNewViewNumber() + ", node" + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validateNewView(NewViewMessage msg) {
        if (msg.getNewViewNumber() <= view.getViewNumber()) {
            logger.warn("NewViewMessage: Invalid view number: " + msg.getNewViewNumber() + " <= " + view.getViewNumber());
            return false;
        }
        var newLeader = view.leaderInView(msg.getNewViewNumber());
        if (!Crypto.checkSignature(msg, newLeader.publicKey())) {
            logger.warn("NewViewMessage: Invalid signature: " + newLeader.id());
            return false;
        }
        if (!msg.viewChangesValid(f, view.publicKeys())) {
            logger.warn("NewViewMessage: Invalid view change messages: " + msg.getNewViewNumber());
            return false;
        }
        if (!msg.prePreparesValid(view.getPrimary().publicKey(), this.f)) {
            logger.warn("NewViewMessage: Invalid pre-prepare messages: " + msg.getNewViewNumber());
            return false;
        }
        return true;
    }

    private boolean validatePullRequests(PullRequestsMessage msg) {
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PullRequestsMessage: Invalid signature: " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validatePullRequestsReply(PullRequestsReplyMessage msg) {
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PullRequestsReplyMessage: Invalid signature: " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validatePullHashes(PullHashesMessage msg) {
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PullHashesMessage: Invalid signature: " + msg.getNodeId());
            return false;
        }
        return true;
    }

    private boolean validatePullHashesReply(PullHashesReplyMessage msg) {
        if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
            logger.warn("PushHashesReplyMessage: Invalid signature: " + msg.getNodeId());
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
