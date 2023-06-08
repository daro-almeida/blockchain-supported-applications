package blockchain;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.notifications.ExecutedOperation;
import blockchain.requests.*;
import blockchain.timers.*;
import consensus.PBFTProtocol;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Node;
import utils.SignaturesHelper;
import utils.View;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.*;
import java.util.stream.Collectors;

public class BlockChainProtocol extends GenericProtocol {

	private static final String PROTO_NAME = "blockchain";
	public static final short PROTO_ID = 200;
	private static final int START_INTERVAL = 2000;

	public static final String PERIOD_CHECK_REQUESTS = "check_requests_period";
	public static final String SUSPECT_LEADER_TIMEOUT = "suspect_leader_timeout";

	private static final Logger logger = LogManager.getLogger(BlockChainProtocol.class);

	private PrivateKey key;

	private final long checkRequestsPeriod;
	private final long suspectLeaderTimeout;
	private final long requestTimeout;
	private final long liveTimeout;
	private final long noOpTimeout;
	private final long forceBlockTimeout;
	private final long maxOps;

	private long leaderIdleTimer = -1;
	private long noOpTimer = -1;
	private long forceBlockTimer = -1;
	private final Map<UUID, Long> leaderSuspectTimers = new HashMap<>();

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();
	// <requestId, set<nodeId>>
	private final Map<UUID, Set<Integer>> nodesSuspectedPerRequest = new HashMap<>();

	private final BlockChain blockChain;

	private Node self;
	private View view;
	private int f;

	// test fault parameters
	private final int ignoreRequestBlock;
	private int invalidBlock;
	private final boolean validateOwnBlocks;

	public BlockChainProtocol(Properties props) throws NumberFormatException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		// Read timers and timeouts configurations
		this.checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		this.suspectLeaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
		this.requestTimeout = Long.parseLong(props.getProperty("request_timeout", "3000"));
		this.liveTimeout = Long.parseLong(props.getProperty("leader_live_timeout", "5000"));
		this.noOpTimeout = Long.parseLong(props.getProperty("noop_timeout", "2500"));
		this.forceBlockTimeout = Long.parseLong(props.getProperty("force_block_timeout", "1000"));
		this.maxOps = Long.parseLong(props.getProperty("max_ops_per_block", "100"));
		this.ignoreRequestBlock = Integer.parseInt(props.getProperty("ignore_request_block", "-1"));
		this.invalidBlock = Integer.parseInt(props.getProperty("invalid_block", "-1"));
		this.validateOwnBlocks = Boolean.parseBoolean(props.getProperty("validate_own_blocks", "true"));

		this.blockChain = new BlockChain(maxOps);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {
		registerRequestHandler(ClientRequest.REQUEST_ID, this::handleClientRequest);
		registerRequestHandler(BlockRequest.REQUEST_ID, this::handleBlockRequest);
		registerRequestHandler(HashRequest.REQUEST_ID, this::handleHashRequest);

		registerTimerHandler(CheckUnhandledRequestsPeriodicTimer.TIMER_ID,
				this::handleCheckUnhandledRequestsPeriodicTimer);
		registerTimerHandler(LeaderSuspectTimer.TIMER_ID, this::handleLeaderSuspectTimer);
		registerTimerHandler(LeaderIdleTimer.TIMER_ID, this::handleLeaderIdleTimer);
		registerTimerHandler(NoOpTimer.TIMER_ID, this::handleNoOpTimer);
		registerTimerHandler(ForceBlockTimer.TIMER_ID, this::handleForceBlockTimer);

		subscribeNotification(ViewChange.NOTIFICATION_ID, this::handleViewChangeNotification);
		subscribeNotification(CommittedNotification.NOTIFICATION_ID, this::handleCommittedNotification);
		subscribeNotification(InitializedNotification.NOTIFICATION_ID, this::handleInitializedNotification);
	}

	private void handleInitializedNotification(InitializedNotification notif, short protoID) {
		this.self = notif.getSelf();
		this.key = notif.getKey();
		this.view = notif.getView();
		this.f = (view.size() - 1) / 3;
		assert this.f > 0;

		var peerChannel = notif.getPeerChannel();

		registerSharedChannel(peerChannel);
		try {
			registerMessageHandler(peerChannel, ClientRequestUnhandledMessage.MESSAGE_ID,
					this::handleClientRequestUnhandledMessage);
			registerMessageHandler(peerChannel, RedirectClientRequestMessage.MESSAGE_ID,
					this::handleRedirectClientRequestMessage);
			registerMessageHandler(peerChannel, StartClientRequestSuspectMessage.MESSAGE_ID,
					this::handleStartClientRequestSuspectMessage);
		} catch (HandlerRegistrationException e) {
			throw new RuntimeException(e);
		}
		registerMessageSerializer(peerChannel, ClientRequestUnhandledMessage.MESSAGE_ID,
				ClientRequestUnhandledMessage.serializer);
		registerMessageSerializer(peerChannel, RedirectClientRequestMessage.MESSAGE_ID,
				RedirectClientRequestMessage.serializer);
		registerMessageSerializer(peerChannel, StartClientRequestSuspectMessage.MESSAGE_ID,
				StartClientRequestSuspectMessage.serializer);

		if (amLeader()) {
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout + START_INTERVAL);
		} else {
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout + START_INTERVAL);
		}
		setupPeriodicTimer(new CheckUnhandledRequestsPeriodicTimer(), checkRequestsPeriod + START_INTERVAL,
				checkRequestsPeriod);
	}

	/*
	 * REQUEST HANDLERS
	 */

	private void handleClientRequest(ClientRequest req, short protoID) {
		assert this.view != null;

		logger.debug("Received client request: " + req.getRequestId());

		if (blockChain.containsOperation(req.getRequestId())) {
			logger.debug("Received repeated request from app: {} ", req.getRequestId());
			return;
		}

		if (amLeader()) {
			submitRequest(req, false);
		} else {
			var message = new RedirectClientRequestMessage(req, this.self.id());
			Crypto.signMessage(message, this.key);

			logger.debug("Redirecting: " + req.getRequestId());
			sendMessage(message, this.view.getPrimary().host());

			pendingRequests.put(req.getRequestId(), new PendingRequest(req, System.currentTimeMillis()));
		}
	}

	private void handleBlockRequest(BlockRequest req, short sourceProtoId) {
		assert this.view != null;

		var blocksWithSignatures = new HashMap<Integer, ProposeRequest>();
		req.getBlocksWanted().forEach(n -> {
			Block b = blockChain.getBlockByConsensusSeq(n);
			var proposeReq = new ProposeRequest(b.serialized(), b.getSignature());
			blocksWithSignatures.put(n, proposeReq);
		});
		BlockReply r = new BlockReply(blocksWithSignatures, this.self.id());
		sendReply(r, PBFTProtocol.PROTO_ID);
	}

	private void handleHashRequest(HashRequest req, short sourceProtoId) {
		assert this.view != null;

		var hashes = new HashMap<Integer, byte[]>();
		req.getHashesWanted().forEach(n -> {
			Block b = blockChain.getBlockByConsensusSeq(n);
			// here it's hash of whole block not hash of rest of contents
			hashes.put(n, Crypto.digest(b.serialized()));
		});
		HashReply r = new HashReply(hashes, this.self.id());
		sendReply(r, PBFTProtocol.PROTO_ID);
	}

	/*
	 * NOTIFICATION HANDLERS
	 */

	private void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		// consensus shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.warn("New view change (" + notif.getView().getViewNumber() + ") primary: node"
				+ notif.getView().getPrimary().id());
		this.view = notif.getView();

		leaderSuspectTimers.keySet().forEach(reqId -> cancelTimer(leaderSuspectTimers.get(reqId)));
		leaderSuspectTimers.clear();

		pendingRequests.forEach((reqId, pendingRequest) -> {
			pendingRequest.setTimestamp(System.currentTimeMillis());
			handleClientRequest(pendingRequest.request(), BlockChainProtocol.PROTO_ID);
		});

		cancelTimer(noOpTimer);
		cancelTimer(leaderIdleTimer);
		cancelTimer(forceBlockTimer);
		blockChain.resetPendings();
		if (amLeader()) {
			pendingRequests.clear();
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
	}

	private void handleCommittedNotification(CommittedNotification notif, short protoID) {
		byte[] blockBytes = notif.getBlock();

		if (isNoOp(blockBytes))
			return;

		var block = Block.deserialize(blockBytes);
		try {
			if ((block.getReplicaId() != this.self.id()) || validateOwnBlocks)
				blockChain.validateBlock(block);
		} catch (InvalidBlockException e) {
			logger.warn("Invalid block, consensusSeqN={}, {}", notif.getSeqN(), e.getMessage());

			if (block.getReplicaId() == view.getPrimary().id()) {
				logger.warn("Suspecting leader");
				sendRequest(new SuspectLeader(view.getViewNumber()), PBFTProtocol.PROTO_ID);
			} else {
				// send all pending ops
				logger.warn("Re-handling pending requests from invalid block of old leader");
				block.getOperations().stream()
						.distinct()
						.filter(req -> req.checkSignature() && pendingRequests.containsKey(req.getRequestId()))
						.forEach(req -> handleClientRequest(req, BlockChainProtocol.PROTO_ID));

			}
			return;
		}

		block.setSignature(notif.getSignature());
		var seqN = blockChain.addBlock(notif.getSeqN(), block);
		logger.info("Committed block seqN={} consensusSeqN={}", seqN, notif.getSeqN());

		block.getOperations().forEach(req -> {
			pendingRequests.remove(req.getRequestId());
			var requestSuspectTimer = leaderSuspectTimers.remove(req.getRequestId());
			if (requestSuspectTimer != null) {
				// all requests for this timer have been committed, so cancel it
				if (leaderSuspectTimers.values().stream().noneMatch(timer -> timer.equals(requestSuspectTimer))) {
					cancelTimer(requestSuspectTimer);
				}
			}
			triggerNotification(new ExecutedOperation(req));
		});

		if (!amLeader()) {
			cancelTimer(leaderIdleTimer);
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
	}

	/*
	 * MESSAGE HANDLERS
	 */

	private void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol,
			int channelId) {
		if (!validateRedirectClientRequestMessage(msg))
			return;

		// checking if request is repeated
		if (blockChain.containsOperation(msg.getRequest())) {
			logger.debug("Received repeated request from node{}: {} ", msg.getNodeId(), msg.getRequest().getRequestId());
			return;
		}

		submitRequest(msg.getRequest(), true);
	}

	private void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender,
			short sourceProtocol, int channelId) {
		if (!validateHandleClientRequestUnhandledMessage(msg))
			return;

		logger.debug("Received unhandled requests from node{}", msg.getNodeId());

		Set<UUID> unhandledRequestsHere = msg.getRequests().stream()
				.filter(req -> !blockChain.containsOperation(req))
				.map(ClientRequest::getRequestId)
				.collect(Collectors.toSet());

		if (unhandledRequestsHere.isEmpty())
			return;

		var suspectMessage = new StartClientRequestSuspectMessage(unhandledRequestsHere, this.self.id());
		Crypto.signMessage(suspectMessage, this.key);

		processSuspectsIds(unhandledRequestsHere, msg.getNodeId());
		processSuspectsIds(unhandledRequestsHere, self.id());

		view.forEach(node -> {
			if (!node.equals(self) && !node.equals(view.getPrimary()))
				sendMessage(suspectMessage, node.host());
		});
	}

	private void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender,
			short sourceProtocol, int channelId) {
		if (!validateHandleStartClientRequestSuspectMessage(msg)) {
			return;
		}

		Set<UUID> unhandledRequestsHere = msg.getRequestIds().stream()
				.filter(req -> !blockChain.containsOperation(req))
				.collect(Collectors.toSet());
		if (unhandledRequestsHere.isEmpty())
			return;

		processSuspectsIds(unhandledRequestsHere, msg.getNodeId());
	}

	/*
	 * TIMER HANDLERS
	 */

	private void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		Set<ClientRequest> unhandledRequests = new HashSet<>();
		pendingRequests.forEach((reqId, req) -> {
			if (req.timestamp() <= System.currentTimeMillis() - requestTimeout &&
					!leaderSuspectTimers.containsKey(reqId)) {
				unhandledRequests.add(req.request());
			}
		});
		if (unhandledRequests.isEmpty())
			return;
		logger.warn("Requests unhandled for too long!");

		var message = new ClientRequestUnhandledMessage(unhandledRequests, self.id());
		Crypto.signMessage(message, this.key);
		view.forEach(node -> {
			if (!node.equals(self) && !node.equals(view.getPrimary()))
				sendMessage(message, node.host());
		});

		processSuspects(unhandledRequests, self.id());
	}

	private void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		logger.warn("Leader suspect timer expired, suspecting leader");
		sendRequest(new SuspectLeader(view.getViewNumber()), PBFTProtocol.PROTO_ID);
	}

	private void handleNoOpTimer(NoOpTimer timer, long l) {
		if (!amLeader())
			return;
		var noOpBytes = new byte[0];
		var signature = SignaturesHelper.generateSignature(noOpBytes, this.key);
		var propose = new ProposeRequest(noOpBytes, signature);
		logger.info("Proposing no-op");
		sendRequest(propose, PBFTProtocol.PROTO_ID);
		noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
	}

	private void handleLeaderIdleTimer(LeaderIdleTimer timer, long l) {
		logger.warn("Leader idle for too long, suspecting leader");
		var suspectLeader = new SuspectLeader(view.getViewNumber());
		sendRequest(suspectLeader, PBFTProtocol.PROTO_ID);
	}

	private void handleForceBlockTimer(ForceBlockTimer timer, long l) {
		if (blockChain.nextBlockSize() == 0)
			return;
		logger.warn("Forcing block creation");
		submitBlock(true);
	}

	/*
	 * AUXILIARY METHODS
	 */

	private void processSuspectsIds(Set<UUID> requestIds, int nodeId) {
		if (this.self.id() != nodeId)
			logger.warn("Received valid suspect message for requests from node" + nodeId);

		Set<UUID> suspectedRequests = new HashSet<>();
		for (var reqId : requestIds) {
			nodesSuspectedPerRequest.computeIfAbsent(reqId, (k -> new HashSet<>())).add(nodeId);
			if (nodesSuspectedPerRequest.get(reqId).size() < f + 1 || leaderSuspectTimers.containsKey(reqId))
				return;
			suspectedRequests.add(reqId);
		}
		if (suspectedRequests.isEmpty())
			return;

		var timerId = setupTimer(new LeaderSuspectTimer(), suspectLeaderTimeout);
		for (var reqId : suspectedRequests)
			leaderSuspectTimers.put(reqId, timerId);

		logger.warn("Starting suspect leader timer for requests.");
	}

	private boolean isNoOp(byte[] op) {
		if (Arrays.equals(op, new byte[0])) {
			logger.info("Received no-op");
			if (!amLeader()) {
				cancelTimer(leaderIdleTimer);
				leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
			}
			return true;
		} else
			return false;
	}

	private void processSuspects(Set<ClientRequest> requestIds, int nodeId) {
		processSuspectsIds(requestIds.stream().map(ClientRequest::getRequestId).collect(Collectors.toSet()), nodeId);
	}

	private boolean validateRedirectClientRequestMessage(RedirectClientRequestMessage msg) {
		var request = msg.getRequest();

		if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("RedirectClientRequestMessage: Invalid signature from node" + msg.getNodeId());
			return false;
		}
		if (!request.checkSignature()) {
			logger.warn("RedirectClientRequestMessage: Invalid request signature from node" + msg.getNodeId());
			return false;
		}

		return true;
	}

	private boolean validateHandleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg) {
		if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("StartClientRequestSuspectMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}
		return true;
	}

	private boolean validateHandleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg) {
		if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("ClientRequestUnhandledMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}

		for (var request : msg.getRequests()) {
			if (!request.checkSignature()) {
				logger.warn("ClientRequestUnhandledMessage: Invalid request signature: " + msg.getNodeId());
				return false;
			}
		}
		return true;
	}

	/*
	 * Create block and fill with ops. When block is full, send to Consensus Protocol.
	 */
	private void submitBlock(boolean force) {
		var size = blockChain.nextBlockSize();

		assert amLeader();
		assert size > 0;
		assert size <= maxOps;

		if (size == 1 && !force) {
			forceBlockTimer = setupTimer(new ForceBlockTimer(), forceBlockTimeout);
		} else if (size == maxOps || force) {
			var block = blockChain.newBlock(self.id());

			if (invalidBlock == blockChain.size())
				invalidateBlock(block);

			var signature = block.sign(this.key);
			var blockBytes = block.serialized();
			logger.info("Proposing block with " + size + " operations");
			sendRequest(new ProposeRequest(blockBytes, signature), PBFTProtocol.PROTO_ID);
			cancelTimer(forceBlockTimer);
		}
	}

	private void invalidateBlock(Block block) {
		block.getOperations().add(block.getOperations().get(0));
		logger.warn("Invalidating block {} because I am evil", invalidBlock);
		invalidBlock = -1;
	}

	private void submitRequest(ClientRequest req, boolean redirected) {
		if (!amLeader())
			return;

		if (ignoreRequest(redirected))
			return;

		blockChain.addOpToNextBlock(req);
		submitBlock(false);
		cancelTimer(noOpTimer);
		noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
	}

	private boolean ignoreRequest(boolean redirected) {
		if (blockChain.size() == ignoreRequestBlock && redirected) {
			logger.warn("Ignoring request from block {} because I am evil", ignoreRequestBlock);
			return true;
		}
		return false;
	}

	private boolean amLeader() {
		return this.self.equals(this.view.getPrimary());
	}

	/*
	 * For RandomGenerationApp
	 */
	public void submitClientOperation(byte[] b) {
		assert view != null;

		// generate key pair
		KeyPair keyPair;
		try {
			keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		var publicKey = keyPair.getPublic();
		var privateKey = keyPair.getPrivate();
		var req = new ClientRequest(b, publicKey, privateKey);

		sendRequest(req, BlockChainProtocol.PROTO_ID);
	}
}
