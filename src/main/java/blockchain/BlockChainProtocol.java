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
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.SignaturesHelper;

public class BlockChainProtocol extends GenericProtocol {

	private static final String PROTO_NAME = "blockchain";
	private static final short PROTO_ID = 200;
	
	public static final String ADDRESS_KEY = "address";
	public static final String PORT_KEY = "base_port";
	public static final String INITIAL_MEMBERSHIP_KEY = "initial_membership";
	
	public static final String PERIOD_CHECK_REQUESTS = "check_requests_timeout";
	public static final String SUSPECT_LEADER_TIMEOUT = "leader_timeout";
	
	private static final Logger logger = LogManager.getLogger(BlockChainProtocol.class);
	
	private String cryptoName;
	private KeyStore truststore;
	private PrivateKey key;
	
	private final long checkRequestsPeriod;
	private final long leaderTimeout;
 	
	private Host self;
	private int viewNumber;
	private final List<Host> view;
	private boolean leader;
	
	public BlockChainProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(BlockChainProtocol.PROTO_NAME, BlockChainProtocol.PROTO_ID);

		//Probably the following informations could be provided by a notification
		//emitted by the PBFTProtocol
		//(this should not be interpreted as the unique or canonical solution)
		self = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
				Integer.parseInt(props.getProperty(PORT_KEY)));
		
		viewNumber = 0;
		view = new LinkedList<>();
		
		//Read timers and timeouts configurations
		checkRequestsPeriod = Long.parseLong(props.getProperty(PERIOD_CHECK_REQUESTS));
		leaderTimeout = Long.parseLong(props.getProperty(SUSPECT_LEADER_TIMEOUT));
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		try {
			cryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
			truststore = Crypto.getTruststore(props);
			key = Crypto.getPrivateKey(cryptoName, props);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
				| IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		registerRequestHandler(ClientRequest.REQUEST_ID, this::handleClientRequest);
		
		registerTimerHandler(CheckUnhandledRequestsPeriodicTimer.TIMER_ID, this::handleCheckUnhandledRequestsPeriodicTimer);
		registerTimerHandler(LeaderSuspectTimer.TIMER_ID, this::handleLeaderSuspectTimer);
		
		subscribeNotification(ViewChange.NOTIFICATION_ID, this::handleViewChangeNotification);
		subscribeNotification(CommittedNotification.NOTIFICATION_ID, this::handleCommittedNotification);
		
		setupPeriodicTimer(new CheckUnhandledRequestsPeriodicTimer(), checkRequestsPeriod, checkRequestsPeriod);
	}
	
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- REQUEST HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	public void handleClientRequest(ClientRequest req, short protoID) {
		logger.info("Received a ClientRequeest with id: " + req.getRequestId());
		
		if(this.leader) {
			
			try {
				//TODO: This is a super over simplification we will handle latter
				//Only one block should be submitted for agreement at a time
				//Also this assumes that a block only contains a single client request
				byte[] request = req.generateByteRepresentation();
				byte[] signature = SignaturesHelper.generateSignature(request, this.key);
				
				sendRequest(new ProposeRequest(request, signature), PBFTProtocol.PROTO_ID);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1); //Catastrophic failure!!!
			}
		} else {
			//TODO: Redirect this request to the leader via a specialized message
		}
	}
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ------------------------------------------- NOTIFICATION HANDLER --------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	public void handleViewChangeNotification(ViewChange vc, short from) {
		logger.info("New view received (" + vc.getViewNumber() + ")");
		
		//TODO: Should maybe validate this ViewChange :)
		
		this.viewNumber = vc.getViewNumber();
		this.view.clear();
		for(int i = 0; i < vc.getView().size(); i++) {
			this.view.add(vc.getView().get(i));
		}
		//TODO: Compute correctly who is the leader and not assume that you are always the leader.
		this.leader = true;
		
	}
	
	public void handleCommittedNotification(CommittedNotification cn, short from) {
		//TODO: write this handler
	}
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ---------------------------------------------- MESSAGE HANDLER ----------------------------------------- */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	//TODO: add message handlers (and register them)

	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- TIMER HANDLER ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    
	public void handleCheckUnhandledRequestsPeriodicTimer(CheckUnhandledRequestsPeriodicTimer t, long timerId) {
		//TODO: write this handler
	}
	
	public void handleLeaderSuspectTimer(LeaderSuspectTimer t, long timerId) {
		//TODO: write this handler
	}
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    public void submitClientOperation(byte[] b) {
    	sendRequest(new ClientRequest(b), BlockChainProtocol.PROTO_ID);
    }

}
