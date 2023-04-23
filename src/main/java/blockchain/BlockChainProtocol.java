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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.Node;
import utils.SignaturesHelper;
import utils.View;

import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.util.*;

public class BlockChainProtocol extends GenericProtocol {

	private static final String PROTO_NAME = "blockchain";
	private static final short PROTO_ID = 200;

	public static final String PERIOD_CHECK_REQUESTS = "check_requests_timeout";
	public static final String SUSPECT_LEADER_TIMEOUT = "leader_timeout";

	private static final Logger logger = LogManager.getLogger(BlockChainProtocol.class);

	private PrivateKey key;

	private final long checkRequestsPeriod;
	private final long suspectLeaderTimeout;
	private final long requestTimeout;
	private final long liveTimeout;
	private final long noOpTimeout; // should be shorter than liveTimeout

	private long leaderIdleTimer = -1;
	private final Map<UUID, Long> leaderSuspectTimers= new HashMap<>();
	private long noOpTimer = -1;

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();

	private Node self;
	private View view;
    private int f;

	private final Map<UUID, Set<StartClientRequestSuspectMessage>> suspectMessages = new HashMap<>();

	private final BlockChain blockChain;
	private Block nextBlock;

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

		setupPeriodicTimer(new CheckUnhandledRequestsPeriodicTimer(), checkRequestsPeriod, checkRequestsPeriod);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- REQUEST HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleClientRequest(ClientRequest req, short protoID) {
		assert this.view != null;

		byte[] request = req.generateByteRepresentation();
		//FIXME request should be signed by client and not by this replica, but for now it's ok because teachers didn't implement this yet :)
		byte[] signature = SignaturesHelper.generateSignature(request, this.key);

		if(this.view.getPrimary().equals(this.self)) {
			cancelTimer(noOpTimer);
			//Only one block should be submitted for agreement at a time
			//FIXME This assumes that a block only contains a single client request, okay for now implement many requests per block later
			var propose = new ProposeRequest(request, signature);
			logger.info("Proposing operation: " + req.getRequestId());
			sendRequest(propose, PBFTProtocol.PROTO_ID);
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			var message = new RedirectClientRequestMessage(req, signature, this.self.id());
			Crypto.signMessage(message, this.key);

			logger.info("Redirecting operation: " + req.getRequestId());
			sendMessage(message, this.view.getPrimary().host());

			pendingRequests.put(req.getRequestId(), new PendingRequest(message, System.currentTimeMillis()));
		}
	}

	public void handleBlockRequest(BlockRequest req, short sourceProtoId) {

		//TODO reply with block (sendReply)
		//FIXME need blockchain to implement this
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ------------------------------------------- NOTIFICATION HANDLER --------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		// pbft shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.info("New view change (" + notif.getView().getViewNumber() + ") primary: node" + notif.getView().getPrimary().id());
		this.view = notif.getView();

		leaderSuspectTimers.keySet().forEach( reqId -> {
			cancelTimer(leaderSuspectTimers.get(reqId));
			var pendingRequest = pendingRequests.get(reqId);
			if (pendingRequest != null) {
				var clientRequest = pendingRequest.message().getRequest();
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

	public void handleCommittedNotification(CommittedNotification notif, short protoID) {
		//TODO check if requests are repeated
		//FIXME assuming blocks are one request for now
		if (Arrays.equals(notif.getBlock(), new byte[0])) {
			logger.info("Received no-op");
			if (!this.view.getPrimary().equals(this.self))
				cancelTimer(leaderIdleTimer);
			return;
		}

		var request = ClientRequest.fromBytes(notif.getBlock());
		logger.info("Committed operation: " + request.getRequestId());

		pendingRequests.remove(request.getRequestId());

		if (!this.view.getPrimary().equals(this.self)) {
			leaderSuspectTimers.remove(request.getRequestId());
			cancelTimer(leaderIdleTimer);
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
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
			noOpTimer = setupTimer(new NoOpTimer(), noOpTimeout);
		} else {
			leaderIdleTimer = setupTimer(new LeaderIdleTimer(), liveTimeout);
		}
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol, int channelId) {
		//TODO check if requests are repeated (probably do that inside below method)
		if(!validateRedirectClientRequestMessage(msg))
			return;

		//FIXME This assumes that a block only contains a single client request, okay for now implement many requests per block later
		var requestBytes = msg.getRequest().generateByteRepresentation();
		var signature = SignaturesHelper.generateSignature(msg.getRequest().generateByteRepresentation(), this.key);
		var propose = new ProposeRequest(requestBytes, signature);
		logger.info("Proposing redirected operation: " + msg.getRequest().getRequestId());
		sendRequest(propose, PBFTProtocol.PROTO_ID);
		cancelTimer(noOpTimer);
	}

	public void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender, short sourceProtocol, int channelId) {
		//FIXME for now can't check request signature (signed by the client) and checking the blockchain
		if(!validateHandleClientRequestUnhandledMessage(msg))
			return;

		var suspectFromRequestUnhandled = new StartClientRequestSuspectMessage(msg.getRequest().getRequestId(), msg.getNodeId());
		suspectMessages.computeIfAbsent(msg.getRequest().getRequestId(), (k -> new HashSet<>())).add(suspectFromRequestUnhandled);

		var suspectMessage = new StartClientRequestSuspectMessage(msg.getRequest().getRequestId(), this.self.id());
		Crypto.signMessage(suspectMessage, this.key);

		processSuspectMessage(suspectMessage);
		view.forEach(node -> {
			if (!node.equals(self))
				sendMessage(suspectMessage, node.host());
		});
	}

	public void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender, short sourceProtocol, int channelId) {
		//FIXME add condition to check if request is in blockchain already
		if(!validateHandleStartClientRequestSuspectMessage(msg)){
			return;
		}

		processSuspectMessage(msg);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	//TODO optimize later to send multiple requests at once
	public void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		pendingRequests.forEach( (reqId, req) -> {
			if(req.timestamp() <= System.currentTimeMillis() - requestTimeout &&
					!leaderSuspectTimers.containsKey(reqId)) {
				logger.warn("Request " + reqId + " unhandled for too long!");
				var message = new ClientRequestUnhandledMessage(req.message().getRequest(),
						req.message().getRequestSignature(), self.id());
				view.forEach(node -> {
					if (!node.equals(self))
						sendMessage(message, node.host());
				} );

				var suspectFromRequestUnhandled = new StartClientRequestSuspectMessage(reqId, self.id());
				processSuspectMessage(suspectFromRequestUnhandled);
			}
		});
	}

	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
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

	public void processSuspectMessage(StartClientRequestSuspectMessage msg) {
		logger.warn("Received suspect message for request " + msg.getRequestId());

		suspectMessages.computeIfAbsent(msg.getRequestId(), (k -> new HashSet<>())).add(msg);
		if(suspectMessages.get(msg.getRequestId()).size() < f + 1 || leaderSuspectTimers.containsKey(msg.getRequestId()))
			return;
		var timerId = setupTimer(new LeaderSuspectTimer(), suspectLeaderTimeout);
		leaderSuspectTimers.put(msg.getRequestId(), timerId);

		logger.warn("Starting suspect leader timer for request " + msg.getRequestId());
	}

	public boolean validateRedirectClientRequestMessage(RedirectClientRequestMessage msg) {
		byte[] request = msg.getRequest().generateByteRepresentation();
		byte[] signature = msg.getRequestSignature();

		if(!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("RedirectClientRequestMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}
		//FIXME for now can't check request signature (signed by the client)
		if(!SignaturesHelper.checkSignature(request, signature, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("RedirectClientRequestMessage: Invalid request signature: " + msg.getNodeId());
			return false;
		}

		return true;
	}

	public boolean validateHandleStartClientRequestSuspectMessage (StartClientRequestSuspectMessage msg){
		if (!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("StartClientRequestSuspectMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}
		return true;
	}

	public boolean validateHandleClientRequestUnhandledMessage (ClientRequestUnhandledMessage msg){
		byte[] messageSignature = msg.getRequest().generateByteRepresentation();
		byte[] requestSignature = msg.getRequestSignature();

		if(!Crypto.checkSignature(msg, view.getNode(msg.getNodeId()).publicKey())){
			logger.warn("ClientRequestUnhandledMessage: Invalid signature: " + msg.getNodeId());
			return false;
		}
		//FIXME for now can't check request signature (signed by the client)
		if(!SignaturesHelper.checkSignature(messageSignature, requestSignature, view.getNode(msg.getNodeId()).publicKey())) {
			logger.warn("RedirectClientRequestMessage: Invalid request signature: " + msg.getNodeId());
			return false;
		}
		return true;
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    public void submitClientOperation(byte[] b) {
		assert view != null;
		var req = new ClientRequest(b);
		sendRequest(req, BlockChainProtocol.PROTO_ID);
    }
}
