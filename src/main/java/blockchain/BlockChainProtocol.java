package blockchain;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.requests.BlockRequest;
import blockchain.requests.ClientRequest;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.LeaderSuspectTimer;
import blockchain.requests.PendingRequest;
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
	private final long leaderTimeout;
	private final long requestTimeout;
	private final long liveTimeout;

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();

	private Node self;
	private View view;
    private final int f;

	private final Map<UUID, Set<StartClientRequestSuspectMessage>> suspectMessages = new HashMap<>();
	private final Set<UUID> suspectTimerStarted = new HashSet<>();

	public BlockChainProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		//Read timers and timeouts configurations
		//TODO check timer values later
		this.checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		this.leaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
		this.requestTimeout = Long.parseLong(props.getProperty("request_timeout", "10000"));
		this.liveTimeout = Long.parseLong(props.getProperty("live_timeout", "10000"));

		this.f = (view.size() - 1) / 3;
        assert this.f > 0;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {
		registerRequestHandler(ClientRequest.REQUEST_ID, this::handleClientRequest);
		registerRequestHandler(BlockRequest.REQUEST_ID, this::handleBlockRequest);

		registerTimerHandler(CheckUnhandledRequestsPeriodicTimer.TIMER_ID, this::handleCheckUnhandledRequestsPeriodicTimer);
		registerTimerHandler(LeaderSuspectTimer.TIMER_ID, this::handleLeaderSuspectTimer);

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
			//TODO reset no-op timer
			//Only one block should be submitted for agreement at a time
			//FIXME This assumes that a block only contains a single client request, okay for now implement many requests per block later
			var propose = new ProposeRequest(request, signature);
			logger.info("Proposing operation: " + req.getRequestId());
			sendRequest(propose, PBFTProtocol.PROTO_ID);
		} else {
			var message = new RedirectClientRequestMessage(req, signature, this.self.id());
			Crypto.signMessage(message, this.key);
			sendMessage(message, this.view.getPrimary().host());

			pendingRequests.put(req.getRequestId(), new PendingRequest(req, signature, System.currentTimeMillis()));
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
		//TODO handle requests that were not handled in the previous view

		// pbft shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.info("New view change (" + notif.getView().getViewNumber() + ") primary: node" + notif.getView().getPrimary().id());

		this.view = notif.getView();
	}

	public void handleCommittedNotification(CommittedNotification notif, short protoID) {
		//TODO check if requests are repeated
		//TODO start a timer (only for leader) that waits for the next request (requests should be sent every so often even if no-op)

		//FIXME assuming blocks are one request for now
		var request = ClientRequest.fromBytes(notif.getBlock());
		logger.info("Committed operation: " + request.getRequestId());

		pendingRequests.remove(request.getRequestId());
		//FIXME how to cancel only timer associated with request????
		cancelTimer(LeaderSuspectTimer.TIMER_ID);
	}

	private void handleInitializedNotification(InitializedNotification notif, short protoID) {
		this.self = notif.getSelf();
		this.key = notif.getKey();
		this.view = notif.getView();

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

		//TODO setup a timer to send a no-op (only for leader)
		// no-ops are special requests that are submitted to pbft so backups know that the leader is still alive
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol, int channelId) {
		//TODO check if requests are repeated (probably do that inside below method)
		if(!validateRedirectClientRequestMessage(msg))
			return;

		var propose = new ProposeRequest(msg.getRequest().generateByteRepresentation(), msg.getRequestSignature());
		logger.info("Proposing operation: " + msg.getRequest().getRequestId());
		sendRequest(propose, PBFTProtocol.PROTO_ID);
	}

	public void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender, short sourceProtocol, int channelId) {
		//FIXME for now can't check request signature (signed by the client) and checking the blockchain
		if(!validateHandleClientRequestUnhandledMessage(msg))
			return;

		var suspectFromRequestUnhandled = new StartClientRequestSuspectMessage(msg.getRequest().getRequestId(), msg.getNodeId());
		suspectMessages.computeIfAbsent(msg.getRequest().getRequestId(), (k -> new HashSet<>())).add(suspectFromRequestUnhandled);

		var suspectMessage = new StartClientRequestSuspectMessage(msg.getRequest().getRequestId(), msg.getNodeId());
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
			if(req.timestamp() <= System.currentTimeMillis() - requestTimeout) {
				var message = new ClientRequestUnhandledMessage(req.request(), req.signature(), self.id());
				view.forEach(node -> {
					if (!node.equals(self))
						sendMessage(message, node.host());
				} );
				var suspectFromRequestUnhandled = new StartClientRequestSuspectMessage(reqId, self.id());
				suspectMessages.computeIfAbsent(reqId, (k -> new HashSet<>())).add(suspectFromRequestUnhandled);
			}
		});
	}

	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		//TODO check again if request is in the chain (not sure if this is necessary), if not send SuspectLeader to pbft

		sendRequest(new SuspectLeader(view.getViewNumber()), PBFTProtocol.PROTO_ID);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
	/* ----------------------------------------------- AUXILIARY FNS ------------------------------------------ */
	/* ----------------------------------------------- ------------- ------------------------------------------ */

	public void processSuspectMessage(StartClientRequestSuspectMessage msg) {
		suspectMessages.computeIfAbsent(msg.getRequestId(), (k -> new HashSet<>())).add(msg);
		if(suspectMessages.get(msg.getRequestId()).size() < f + 1 || suspectTimerStarted.contains(msg.getRequestId()))
			return;
		setupTimer(new LeaderSuspectTimer(msg.getRequestId()), leaderTimeout);
		suspectTimerStarted.add(msg.getRequestId());
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
