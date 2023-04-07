package blockchain;

import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.requests.ClientRequest;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.LeaderSuspectTimer;
import blockchain.utils.PendingRequest;
import consensus.PBFTProtocol;
import consensus.notifications.CommittedNotification;
import consensus.notifications.InitializedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import consensus.requests.SuspectLeader;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidFormatException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.babel.generic.signed.NoSignaturePresentException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Node;
import utils.SignaturesHelper;
import utils.View;

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

	// <requestId, (request, timestamp)>
	private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();

	private Node self;
	private View view;

	public BlockChainProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		//Read timers and timeouts configurations
		this.checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		this.leaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
		this.requestTimeout = 10000; //TODO
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {

		registerRequestHandler(ClientRequest.REQUEST_ID, this::handleClientRequest);

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
		byte[] signature;
		try {
			//FIXME request should be signed by client and not by this replica, but for now it's ok because teachers didn't implement this yet :)
			signature = SignaturesHelper.generateSignature(request, this.key);
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
			throw new RuntimeException(e);
		}

		if(this.view.getPrimary().equals(this.self)) {
			//Only one block should be submitted for agreement at a time
			//FIXME This assumes that a block only contains a single client request, okay for now implement many requests per block later
			var propose = new ProposeRequest(request, signature);
			logger.info("Proposing operation: " + req.getRequestId());
			sendRequest(propose, PBFTProtocol.PROTO_ID);
		} else {
			var message = new RedirectClientRequestMessage(req, signature, this.self.id());
			try {
				message.signMessage(key);
			} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
				throw new RuntimeException(e);
			}
			sendMessage(message, this.view.getPrimary().host());

			pendingRequests.put(req.getRequestId(), new PendingRequest(req, signature, System.currentTimeMillis()));
		}

		
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ------------------------------------------- NOTIFICATION HANDLER --------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		// pbft shouldn't send this if the view is already the same number
		assert notif.getView().getViewNumber() > this.view.getViewNumber();

		logger.info("New view change (" + notif.getView().getViewNumber() + ") primary: node" + notif.getView().getPrimary().id());

		this.view = notif.getView();
	}

	public void handleCommittedNotification(CommittedNotification notif, short protoID) {
		//FIXME assuming blocks are one request for now
		var request = ClientRequest.fromBytes(notif.getBlock());
		logger.info("Committed operation: " + request.getRequestId());
		pendingRequests.remove(request.getRequestId());
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
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleClientRequestUnhandledMessage(ClientRequestUnhandledMessage msg, Host sender, short sourceProtocol, int channelId) {

		byte[] messageSignature = msg.getRequest().generateByteRepresentation();


		try {
			if(!msg.checkSignature(view.getNode(msg.getNodeId()).publicKey())){
				logger.warn("ClientRequestUnhandledMessage: Invalid signature: " + msg.getNodeId());
				return;
			}
			// check if the request is in the blockchain

		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidFormatException
				| NoSignaturePresentException e) {
			logger.warn(e.getMessage());
			return;
		}

		var suspectMessage = new StartClientRequestSuspectMessage(null, 0);

		// send suspectMessage to all replicas

		
		// TODO check signatures (message and request), if valid and if the request is not in the chain send
		// StartClientRequestSuspectMessage to all replicas (including self)
		//FIXME for now can't check request signature (signed by the client) and checking the blockchain
	}

	public void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol, int channelId) {
			if(!validateRedirectClientRequestMessage(msg))
				return;

			var propose = new ProposeRequest(msg.getRequest().generateByteRepresentation(), msg.getRequestSignature());
			logger.info("Proposing operation: " + msg.getRequest().getRequestId());
			sendRequest(propose, PBFTProtocol.PROTO_ID);
	}

	public void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender, short sourceProtocol, int channelId) {


		try {
			if(!msg.checkSignature(view.getNode(msg.getNodeId()).publicKey())){
				logger.warn("StartClientRequestSuspectMessage: Invalid signature: " + msg.getNodeId());
				return;
			}
			// check if got f + 1
			// check if request is in the chain

		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidFormatException
				| NoSignaturePresentException e) {
			logger.warn(e.getMessage());
			return;
		}

		var suspect = new LeaderSuspectTimer(msg.getRequestId());
		handleLeaderSuspectTimer(suspect, 0); // ver qual é o timerId


		//TODO check message signature, if valid and if got f + 1 StartClientRequestSuspectMessages (including this one)
		// and if the request is not in the chain start LeaderSuspectTimer timer
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		/*TODO check pending requests, if any exceeded time limit to be ordered (returned by pbft) send
		ClientRequestUnhandledMessage to all replicas (including self)
		 */
		pendingRequests.forEach( (reqId, req) -> {
			if(req.timestamp() >= requestTimeout) {
				var message = new ClientRequestUnhandledMessage(req.request(), req.signature(), self.id());
				view.forEach(node -> sendMessage(message, node.host()) );
			}
		});
		
	}

	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		//TODO check again if request is in the chain (not sure if this is necessary), if not send SuspectLeader to pbft
		sendRequest(new SuspectLeader(t.getRequestID()), PBFTProtocol.PROTO_ID);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
	/* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
	/* ----------------------------------------------- ------------- ------------------------------------------ */

	public boolean validateRedirectClientRequestMessage(RedirectClientRequestMessage msg) {
		byte[] request = msg.getRequest().generateByteRepresentation();
		byte[] signature = msg.getRequestSignature();

		try {
			if(!msg.checkSignature(view.getNode(msg.getNodeId()).publicKey())) {
				logger.warn("RedirectClientRequestMessage: Invalid signature: " + msg.getNodeId());
				return false;
			}
			//FIXME for now can't check request signature (signed by the client)
			if(!SignaturesHelper.checkSignature(request, signature, view.getNode(msg.getNodeId()).publicKey())) {
				logger.warn("RedirectClientRequestMessage: Invalid request signature: " + msg.getNodeId());
				return false;
			}
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidFormatException
				| NoSignaturePresentException e) {
			logger.warn(e.getMessage());
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
