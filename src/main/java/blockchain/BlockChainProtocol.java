package blockchain;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import consensus.notifications.InitializedNotification;
import consensus.requests.SuspectLeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import blockchain.requests.ClientRequest;
import blockchain.timers.CheckUnhandledRequestsPeriodicTimer;
import blockchain.timers.LeaderSuspectTimer;
import consensus.PBFTProtocol;
import consensus.notifications.CommittedNotification;
import consensus.notifications.ViewChange;
import consensus.requests.ProposeRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.*;

import javax.management.RuntimeErrorException;

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
	public void init(Properties props) throws HandlerRegistrationException, IOException {
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
		
		if(this.view.getPrimary().equals(this.self)) {
			try {
				//TODO: This is a super over simplification we will handle later
				//Only one block should be submitted for agreement at a time
				//Also this assumes that a block only contains a single client request
				byte[] request = req.generateByteRepresentation();
				byte[] signature = SignaturesHelper.generateSignature(request, this.key);

				var propose = new ProposeRequest(request, signature);
				logger.info("Sending ProposeRequest with digest: " + Utils.bytesToHex(propose.getDigest()));
				sendRequest(propose, PBFTProtocol.PROTO_ID);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			//TODO: Redirect this request to the leader via a specialized message (not sure if we can do this now :) )
		}
	}
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ------------------------------------------- NOTIFICATION HANDLER --------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	public void handleViewChangeNotification(ViewChange notif, short sourceProtoId) {
		logger.info("New view change (" + notif.getView().getViewNumber() + ") primary: node" + notif.getView().getPrimary().id());

		//TODO NOW
		//TODO: Should maybe validate this ViewChange :)

		//update view (?) not sure because pbft and blockchain should share same reference to view

	}
	
	public void handleCommittedNotification(CommittedNotification notif, short protoID) {
		var digest = new ProposeRequest(notif.getBlock(), notif.getSignature()).getDigest();
		logger.info("Committed operation with digest: " + Utils.bytesToHex(digest));
		//TODO: write this handler
	}

	private void handleInitializedNotification(InitializedNotification notif, short protoID) {
		this.self = notif.getSelf();
		this.key = notif.getKey();
		this.view = notif.getView();
	}
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	public void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		//TODO NOW maybe
	}
	
	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
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
