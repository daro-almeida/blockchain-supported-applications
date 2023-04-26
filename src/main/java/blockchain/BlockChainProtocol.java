package blockchain;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.requests.BlockRequest;
import blockchain.requests.ClientRequest;
import blockchain.requests.PendingRequest;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.LeaderIdleTimer;
import blockchain.timers.LeaderSuspectTimer;
import blockchain.timers.NoOpTimer;
import consensus.PBFTProtocol;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Node;
import utils.SignaturesHelper;
import utils.View;

import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

public class BlockChainProtocol extends GenericProtocol {

	private static final String PROTO_NAME = "blockchain";
	private static final short PROTO_ID = 200;
    private static final int START_INTERVAL = 2000;

	public static final String PERIOD_CHECK_REQUESTS = "check_requests_timeout";
	public static final String SUSPECT_LEADER_TIMEOUT = "leader_timeout";

	private static final Logger logger = LogManager.getLogger(BlockChainProtocol.class);

	private PrivateKey key;

	private final long checkRequestsPeriod;
	private final long suspectLeaderTimeout;
	private final long requestTimeout;
	private final long liveTimeout;
	private final long noOpTimeout;

	private long leaderIdleTimer = -1;
	private long noOpTimer = -1;
	private final Map<UUID, Long> leaderSuspectTimers = new HashMap<>();

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();
	// <requestId, set<nodeId>>
	private final Map<UUID, Set<Integer>> nodesSuspectedPerRequest = new HashMap<>();

	private Node self;
	private View view;
	private int f;

	public BlockChainProtocol(Properties props) throws NumberFormatException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		//Read timers and timeouts configurations
		//TODO check timer values later
		this.checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		this.suspectLeaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
		this.requestTimeout = Long.parseLong(props.getProperty("request_timeout", "3000"));
		this.liveTimeout = Long.parseLong(props.getProperty("live_timeout", "5000"));
		this.noOpTimeout = Long.parseLong(props.getProperty("noop_timeout", "2500"));
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {
		registerRequestHandler(ClientRequest.REQUEST_ID, this::handleClientRequest);
		registerRequestHandler(BlockRequest.REQUEST_ID, this::handleBlockRequest);

		registerTimerHandler(CheckUnhandledRequestsPeriodicTimer.TIMER_ID, this::handleCheckUnhandledRequestsPeriodicTimer);
		registerTimerHandler(LeaderSuspectTimer.TIMER_ID, this::handleLeaderSuspectTimer);
		registerTimerHandler(LeaderIdleTimer.TIMER_ID, this::handleLeaderIdleTimer);
		registerTimerHandler(NoOpTimer.TIMER_ID, this::handleNoOpTimer);

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
			registerMessageHandler(peerChannel, ClientRequestUnhandledMessage.MESSAGE_ID, this::handleClientRequestUnhandledMessage);
			registerMessageHandler(peerChannel, RedirectClientRequestMessage.MESSAGE_ID, this::handleRedirectClientRequestMessage);
			registerMessageHandler(peerChannel, StartClientRequestSuspectMessage.MESSAGE_ID, this::handleStartClientRequestSuspectMessage);
		} catch (HandlerRegistrationException e) {
			throw new RuntimeException(e);
		}
		registerMessageSerializer(peerChannel, ClientRequestUnhandledMessage.MESSAGE_ID, ClientRequestUnhandledMessage.serializer);
		registerMessageSerializer(peerChannel, RedirectClientRequestMessage.MESSAGE_ID, RedirectClientRequestMessage.serializer);
		registerMessageSerializer(peerChannel, StartClientRequestSuspectMessage.MESSAGE_ID, StartClientRequestSuspectMessage.serializer);

		if (this.view.getPrimary().equals(this.self)) {
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout + START_INTERVAL);
		} else {
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout + START_INTERVAL);
		}
		setupPeriodicTimer(new CheckUnhandledRequestsPeriodicTimer(), checkRequestsPeriod + START_INTERVAL, checkRequestsPeriod);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- REQUEST HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	//TODO later implement this for processing new Blocks
	private void handleClientRequest(ClientRequest req, short protoID) {
		assert this.view != null;

		byte[] request = req.toBytes();

		if(this.view.getPrimary().equals(this.self)) {
			//FIXME this is simulating signing block for now
			byte[] signature = SignaturesHelper.generateSignature(request, this.key);
			//FIXME This assumes that a block only contains a single client request, implement many requests per block later
			var propose = new ProposeRequest(request, signature);
			logger.info("Proposing operation: " + req.getRequestId());
			sendRequest(propose, PBFTProtocol.PROTO_ID);

			cancelTimer(noOpTimer);
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			var message = new RedirectClientRequestMessage(req, this.self.id());
			Crypto.signMessage(message, this.key);

			logger.info("Redirecting operation: " + req.getRequestId());
			sendMessage(message, this.view.getPrimary().host());

			pendingRequests.put(req.getRequestId(), new PendingRequest(req, System.currentTimeMillis()));
		}
	}

	private void handleBlockRequest(BlockRequest req, short sourceProtoId) {
		//TODO reply with block (sendReply)
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ------------------------------------------- NOTIFICATION HANDLER --------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	private void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		// consensus shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.info("New view change (" + notif.getView().getViewNumber() + ") primary: node" + notif.getView().getPrimary().id());
		this.view = notif.getView();

		leaderSuspectTimers.keySet().forEach( reqId -> {
			cancelTimer(leaderSuspectTimers.get(reqId));
			var pendingRequest = pendingRequests.get(reqId);
			if (pendingRequest != null) {
				var clientRequest = pendingRequest.request();
				handleClientRequest(clientRequest, BlockChainProtocol.PROTO_ID);
			}
		});
		leaderSuspectTimers.clear();

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

	//TODO later implement this for receiving a Block
	private void handleCommittedNotification(CommittedNotification notif, short protoID) {
		//TODO substitute for checking for NoOpBlock later
		if (Arrays.equals(notif.getBlock(), new byte[0])) {
			logger.info("Received no-op");
			if (!this.view.getPrimary().equals(this.self)) {
				cancelTimer(leaderIdleTimer);
				leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
			}
			return;
		}

		//TODO check if any requests (in Block later) are repeated and valid

		//don't need to do this mess after switching to block
		ClientRequest request = null;
		try {
			request = ClientRequest.serializer.deserialize(Unpooled.wrappedBuffer(notif.getBlock()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (!request.checkSignature()) {
			logger.warn("Committed request with invalid signature: " + request.getRequestId());
			return;
		}

		logger.info("Committed operation: " + request.getRequestId());
		pendingRequests.remove(request.getRequestId());

		if (!this.view.getPrimary().equals(this.self)) {
			var requestSuspectTimer = leaderSuspectTimers.remove(request.getRequestId());
			if (requestSuspectTimer != null) {
				// all requests for this timer have been committed, so cancel it
				if (leaderSuspectTimers.values().stream().noneMatch(timer -> timer.equals(requestSuspectTimer))) {
					cancelTimer(requestSuspectTimer);
				}
			}

			cancelTimer(leaderIdleTimer);
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	private void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol, int channelId) {
		if(!validateRedirectClientRequestMessage(msg))
			return;
		//TODO check if requests are repeated

		var requestBytes = msg.getRequest().toBytes();
		//FIXME this is simulating signing block for now
		var signature = SignaturesHelper.generateSignature(requestBytes, this.key);
		//FIXME This assumes that a block only contains a single client request, implement many requests per block later
		var propose = new ProposeRequest(requestBytes, signature);
		logger.info("Proposing redirected operation: " + msg.getRequest().getRequestId());
		sendRequest(propose, PBFTProtocol.PROTO_ID);
		cancelTimer(noOpTimer);
	}

	private void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender, short sourceProtocol, int channelId) {
		if(!validateHandleClientRequestUnhandledMessage(msg))
			return;

		Set<UUID> unhandledRequestsHere = msg.getRequests().stream()
				.filter(request -> true) //FIXME check if request is already in the blockchain
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

	private void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender, short sourceProtocol, int channelId) {
		if(!validateHandleStartClientRequestSuspectMessage(msg)){
			return;
		}

		Set<UUID> unhandledRequestsHere = msg.getRequestIds().stream()
				.filter(request -> true) //FIXME check if request is already in the blockchain
				.collect(Collectors.toSet());
		if (unhandledRequestsHere.isEmpty())
			return;

		processSuspectsIds(unhandledRequestsHere, msg.getNodeId());
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	private void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		Set<ClientRequest> unhandedRequests = new HashSet<>();
		pendingRequests.forEach( (reqId, req) -> {
			if(req.timestamp() <= System.currentTimeMillis() - requestTimeout &&
					!leaderSuspectTimers.containsKey(reqId)) {
				logger.warn("Request " + reqId + " unhandled for too long!");
				unhandedRequests.add(req.request());
			}
		});
		if (unhandedRequests.isEmpty())
			return;

		var message = new ClientRequestUnhandledMessage(unhandedRequests, self.id());
		view.forEach(node -> {
			if (!node.equals(self))
				sendMessage(message, node.host());
		});

		processSuspects(unhandedRequests, self.id());
	}

	private void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
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

	/* ----------------------------------------------- ------------- ------------------------------------------ */
	/* ----------------------------------------------- AUXILIARY FNS ------------------------------------------ */
	/* ----------------------------------------------- ------------- ------------------------------------------ */

	private void processSuspectsIds(Set<UUID> requestIds, int nodeId) {
		logger.warn("Received valid suspect message for requests " + requestIds + " from node" + nodeId);

		Set<UUID> suspectedRequests = new HashSet<>();
		for (var reqId : requestIds) {
			nodesSuspectedPerRequest.computeIfAbsent(reqId, (k -> new HashSet<>())).add(nodeId);
			if(nodesSuspectedPerRequest.get(reqId).size() < f + 1 || leaderSuspectTimers.containsKey(reqId))
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

	private void processSuspects(Set<ClientRequest> requestIds, int nodeId) {
		processSuspectsIds(requestIds.stream().map(ClientRequest::getRequestId).collect(Collectors.toSet()), nodeId);
	}

	private boolean validateRedirectClientRequestMessage(RedirectClientRequestMessage msg) {
		var request = msg.getRequest();

		if(!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("RedirectClientRequestMessage: Invalid signature from node" + msg.getNodeId());
			return false;
		}
		if(!request.checkSignature()) {
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
		if(!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())){
			logger.warn("ClientRequestUnhandledMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}

		for (var request : msg.getRequests()) {
			if(!request.checkSignature()) {
				logger.warn("ClientRequestUnhandledMessage: Invalid request signature: " + msg.getNodeId());
				return false;
			}
		}
		return true;
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    public void submitClientOperation(byte[] b) {
		assert view != null;

		//TODO temporary
		//generate key pair
		KeyPair keyPair = null;
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
