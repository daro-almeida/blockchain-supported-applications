package blockchain;

import blockchain.messages.ClientRequestUnhandledMessage;
import blockchain.messages.RedirectClientRequestMessage;
import blockchain.messages.StartClientRequestSuspectMessage;
import blockchain.requests.ClientRequest;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.LeaderSuspectTimer;
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
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Node;
import utils.SignaturesHelper;
import utils.Utils;
import utils.View;

import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.Properties;

public class BlockChainProtocol extends GenericProtocol {

	private static final String PROTO_NAME = "blockchain";
	private static final short PROTO_ID = 200;

	public static final String PERIOD_CHECK_REQUESTS = "check_requests_timeout";
	public static final String SUSPECT_LEADER_TIMEOUT = "leader_timeout";

	private static final Logger logger = LogManager.getLogger(BlockChainProtocol.class);

	private PrivateKey key;

	private final long checkRequestsPeriod;
	private final long leaderTimeout;

	private Node self;
	private View view;

	public BlockChainProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		//Read timers and timeouts configurations
		this.checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		this.leaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
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
			// FIXME request should be signed by client and not by this replica, but for now it's ok because teachers didn't implement this yet :)
			signature = SignaturesHelper.generateSignature(request, this.key);
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
			throw new RuntimeException(e);
		}

		if(this.view.getPrimary().equals(this.self)) {
			//Only one block should be submitted for agreement at a time
			// FIXME This assumes that a block only contains a single client request, okay for now implement many requests per block later
			var propose = new ProposeRequest(request, signature);
			logger.info("Sending ProposeRequest with digest: " + Utils.bytesToHex(propose.getDigest()));
			sendRequest(propose, PBFTProtocol.PROTO_ID);
		} else {
			var message = new RedirectClientRequestMessage(req, signature, this.self.id());
			try {
				message.signMessage(key);
			} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
				throw new RuntimeException(e);
			}
			sendMessage(message, this.view.getPrimary().host());
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
		var digest = new ProposeRequest(notif.getBlock(), notif.getSignature()).getDigest();
		logger.info("Committed operation with digest: " + Utils.bytesToHex(digest));
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
		//TODO check signatures (message and request), if valid and if the request is not in the chain send
		// StartClientRequestSuspectMessage to all replicas (including self)
		// FIXME for now can't check request signature (signed by the client) and checking the blockchain
	}

	public void handleRedirectClientRequestMessage(RedirectClientRequestMessage msg, Host sender, short sourceProtocol, int channelId) {
		// TODO check signatures (message and request), if valid propose request to pbft
		// FIXME for now can't check request signature (signed by the client)
	}

	public void handleStartClientRequestSuspectMessage(StartClientRequestSuspectMessage msg, Host sender, short sourceProtocol, int channelId) {
		//TODO check message signature, if valid and if got f + 1 StartClientRequestSuspectMessages (including this one)
		// and if the request is not in the chain start LeaderSuspectTimer timer
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */

	public void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		//TODO check pending requests, if any exceeded time limit to be ordered (returned by pbft) send
		// ClientRequestUnhandledMessage to all replicas (including self)
	}

	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		//TODO check again if request is in the chain (not sure if this is necessary), if not send SuspectLeader to pbft
		sendRequest(new SuspectLeader(t.getRequestID()), PBFTProtocol.PROTO_ID);
	}

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    public void submitClientOperation(byte[] b) {
		assert view != null;

		sendRequest(new ClientRequest(b), BlockChainProtocol.PROTO_ID);
    }

}
