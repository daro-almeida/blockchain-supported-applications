package blockchain;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.notifications.ExecutedOperation;
import blockchain.requests.*;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.ForceBlockTimer;
import blockchain.timers.LeaderIdleTimer;
import blockchain.timers.LeaderSuspectTimer;
import blockchain.timers.NoOpTimer;
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

import java.security.*;
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

	private List<ClientRequest> blockOps = new LinkedList<>();

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();
	// <requestId, set<nodeId>>
	private final Map<UUID, Set<Integer>> nodesSuspectedPerRequest = new HashMap<>();

	private final BlockChain blockChain;

	private Node self;
	private View view;
	private int f;

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

		if (this.view.getPrimary().equals(this.self)) {
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout + START_INTERVAL);
		} else {
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout + START_INTERVAL);
		}
		setupPeriodicTimer(new CheckUnhandledRequestsPeriodicTimer(), checkRequestsPeriod + START_INTERVAL,
				checkRequestsPeriod);
	}

	/*
	 * ---------------------------------------------- REQUEST HANDLER -----------------------------------------
	 */

	// TODO later implement this for processing new Blocks
	private void handleClientRequest(ClientRequest req, short protoID) {
		assert this.view != null;

		logger.info("Proposing: " + req.getRequestId());

		if (!blockChain.containsOperation(req.getRequestId())) {
			logger.warn("Received repeated request from node{}: {} ", req.getRequestId());
			return;
		}

		if (this.view.getPrimary().equals(this.self)) {
			blockOps.add(req);
			submitFullBlock(false);
			cancelTimer(noOpTimer);
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			var message = new RedirectClientRequestMessage(req, this.self.id());
			Crypto.signMessage(message, this.key);

			logger.info("Redirecting: " + req.getRequestId());
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
	 * ------------------------------------------- NOTIFICATION HANDLER
	 * ---------------------------------------
	 */

	private void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		// consensus shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.warn("New view change (" + notif.getView().getViewNumber() + ") primary: node"
				+ notif.getView().getPrimary().id());
		this.view = notif.getView();

		leaderSuspectTimers.keySet().forEach(reqId -> {
			cancelTimer(leaderSuspectTimers.get(reqId));
		});
		leaderSuspectTimers.clear();

		pendingRequests.forEach((reqId, pendingRequest) -> {
			pendingRequest.setTimestamp(System.currentTimeMillis());
			handleClientRequest(pendingRequest.request(), BlockChainProtocol.PROTO_ID);
		});

		if (this.view.getPrimary().equals(this.self)) {
			cancelTimer(noOpTimer);
			cancelTimer(leaderIdleTimer);
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			cancelTimer(noOpTimer);
			cancelTimer(leaderIdleTimer);
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
	}

	private void handleCommittedNotification(CommittedNotification notif, short protoID) {
		byte[] blockBytes = notif.getBlock();

		if (isNoOp(blockBytes))
			return;

		var block = Block.deserialize(blockBytes);
		if (!blockChain.validateBlock(block)) {
			logger.warn("Invalid block seqN=" + notif.getSeqN());

			if (block.getReplicaId() == view.getPrimary().id()) {
				logger.warn("Suspecting leader");
				sendRequest(new SuspectLeader(view.getViewNumber()), PBFTProtocol.PROTO_ID);
			} else {
				// send all pending ops
				logger.debug("Resubmitting pending requests");
				block.getOperations().stream()
						.filter(req -> req.checkSignature() && pendingRequests.containsKey(req.getRequestId()))
						.forEach(req -> handleClientRequest(req, BlockChainProtocol.PROTO_ID));

			}
			return;
		}

		blockChain.addBlock(notif.getSeqN(), block);
		logger.info("Committed block seqN=" + notif.getSeqN());

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

		cancelTimer(leaderIdleTimer);
		leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
	}

	/*
	 * ---------------------------------------------- MESSAGE HANDLER
	 * -----------------------------------------
	 */

	private void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol,
			int channelId) {
		if (!validateRedirectClientRequestMessage(msg))
			return;

		// checking if request is repeated
		if (blockChain.containsOperation(msg.getRequest())) {
			logger.warn("Received repeated request from node{}: {} ", msg.getNodeId(), msg.getRequest().getRequestId());
			return;
		}

		blockOps.add(msg.getRequest());
		submitFullBlock(false);

		cancelTimer(noOpTimer);
	}

	private void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender,
			short sourceProtocol, int channelId) {
		if (!validateHandleClientRequestUnhandledMessage(msg))
			return;

		logger.warn("Received unhandled requests from node{}: {} ", msg.getNodeId(), msg.getRequests());

		Set<UUID> unhandledRequestsHere = msg.getRequests().stream()
				.filter(req -> blockChain.containsOperation(req))
				.map(ClientRequest::getRequestId)
				.collect(Collectors.toSet());

		if (unhandledRequestsHere.isEmpty())
			return;

		var suspectMessage = new StartClientRequestSuspectMessage(unhandledRequestsHere, this.self.id());
		Crypto.signMessage(suspectMessage, this.key);

		processSuspectsIds(unhandledRequestsHere, msg.getNodeId());
		processSuspectsIds(unhandledRequestsHere, self.id());

		view.forEach(node -> {
			if (!node.equals(self))
				sendMessage(suspectMessage, node.host());
		});
	}

	private void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender,
			short sourceProtocol, int channelId) {
		if (!validateHandleStartClientRequestSuspectMessage(msg)) {
			return;
		}

		Set<UUID> unhandledRequestsHere = msg.getRequestIds().stream()
				.filter(req -> blockChain.containsOperation(req))
				.collect(Collectors.toSet());
		if (unhandledRequestsHere.isEmpty())
			return;

		processSuspectsIds(unhandledRequestsHere, msg.getNodeId());
	}

	/*
	 * ----------------------------------------------- TIMER HANDLER
	 * ------------------------------------------
	 */

	private void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		Set<ClientRequest> unhandledRequests = new HashSet<>();
		pendingRequests.forEach((reqId, req) -> {
			if (req.timestamp() <= System.currentTimeMillis() - requestTimeout &&
					!leaderSuspectTimers.containsKey(reqId)) {
				logger.warn("Request " + reqId + " unhandled for too long!");
				unhandledRequests.add(req.request());
			}
		});
		if (unhandledRequests.isEmpty())
			return;

		var message = new ClientRequestUnhandledMessage(unhandledRequests, self.id());
		view.forEach(node -> {
			if (!node.equals(self))
				sendMessage(message, node.host());
		});

		processSuspects(unhandledRequests, self.id());
	}

	private void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		logger.warn("Leader suspect timer expired, suspecting leader");
		sendRequest(new SuspectLeader(view.getViewNumber()), PBFTProtocol.PROTO_ID);
	}

	private void handleNoOpTimer(NoOpTimer timer, long l) {
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
		assert this.self.equals(this.view.getPrimary());

		logger.info("Forcing block creation");
		submitFullBlock(true);

	}

	/*
	 * ----------------------------------------------- AUXILIARY FNS
	 * ------------------------------------------
	 */

	private void processSuspectsIds(Set<UUID> requestIds, int nodeId) {
		logger.warn("Received valid suspect message for requests " + requestIds + " from node" + nodeId);

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

		logger.warn("Starting suspect leader timer for requests " + suspectedRequests);
	}

	private boolean isNoOp(byte[] op) {
		if (Arrays.equals(op, new byte[0])) {
			logger.info("Received no-op");
			if (!this.view.getPrimary().equals(this.self)) {
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
	 * ----------------------------------------------- APP INTERFACE
	 * -----------------------------------------
	 */

	private void submitFullBlock(boolean force) {
		// create block and fill with ops. when block is full, send to pbft
		// (forceblocktimer)

		//setup timer if its the first operation we are receiving
		if (blockOps.size() == 1) {
			forceBlockTimer = setupTimer(new ForceBlockTimer(), forceBlockTimeout);
		}

		var block = blockChain.newBlock(blockOps, self.id());
		var blockBytes = block.serialized();
		if (!blockChain.validateBlock(block)) {
			logger.info("invalid block!");
			return;
		}
		// TODO is this signing the block ??
		var signature = SignaturesHelper.generateSignature(blockBytes, this.key);
		if (blockOps.size() >= maxOps || force) {
			cancelTimer(forceBlockTimer);
			forceBlockTimer = setupTimer(new ForceBlockTimer(), forceBlockTimeout);
			sendRequest(new ProposeRequest(blockBytes, signature), PBFTProtocol.PROTO_ID);
			blockOps = new LinkedList<>();
		}
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
