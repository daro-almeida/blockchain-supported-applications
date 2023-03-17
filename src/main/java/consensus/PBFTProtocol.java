package consensus;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import consensus.requests.ProposeRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.MultithreadedTCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;


public class PBFTProtocol extends GenericProtocol {

	public static final String PROTO_NAME = "pbft";
	public static final short PROTO_ID = 100;
	
	public static final String ADDRESS_KEY = "address";
	public static final String PORT_KEY = "base_port";
	public static final String INITIAL_MEMBERSHIP_KEY = "initial_membership";

	private static final Logger logger = LogManager.getLogger(PBFTProtocol.class);
	
	private String cryptoName;
	private KeyStore truststore;
	private PrivateKey key;
	
	private Host self;
	private int viewN;
	private final List<Host> view;
	private int seq;
	private boolean primary;
	
	public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

		this.seq = 0;
		this.viewN = 0;
		this.primary = Boolean.parseBoolean(props.getProperty("bootstrap_primary","false"));

		self = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
				Integer.parseInt(props.getProperty(PORT_KEY)));
		
		view = new LinkedList<>();
		String[] membership = props.getProperty(INITIAL_MEMBERSHIP_KEY).split(",");
		for (String s : membership) {
			String[] tokens = s.split(":");
			var host = new Host(InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1]));
			if (!host.equals(self))
				view.add(host);
		}
	}



	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		try {
			cryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
			truststore = Crypto.getTruststore(props);
			key = Crypto.getPrivateKey(cryptoName, props);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
				| IOException e) {
			e.printStackTrace();
		}

		Properties peerProps = new Properties();
		peerProps.put(MultithreadedTCPChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
		peerProps.setProperty(TCPChannel.PORT_KEY, props.getProperty(PORT_KEY));
		int peerChannel = createChannel(TCPChannel.NAME, peerProps);

		logger.info("Standing by to establish connections (2s)");

		registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);

		registerMessageHandler(peerChannel, PrePrepareMessage.MESSAGE_ID, this::uponPrePrepareMessage);
		registerMessageHandler(peerChannel, PrepareMessage.MESSAGE_ID, this::uponPrepareMessage);
		registerMessageHandler(peerChannel, CommitMessage.MESSAGE_ID, this::uponCommitMessage);

		registerChannelEventHandler(peerChannel, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
		registerChannelEventHandler(peerChannel, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(peerChannel, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(peerChannel, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(peerChannel, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);

		try { Thread.sleep(2 * 1000); } catch (InterruptedException ignored) { }
		
		view.forEach(this::openConnection);
	}

	/* --------------------------------------- Predicates ----------------------------------- */

	// not sure yet if m should be byte[] or ProposeRequest
	private boolean prepared(ProposeRequest m, int v, int n) {
		// TODO: Implement
		return false;
	}
	private boolean committed(ProposeRequest m, int v, int n) {
		// TODO: Implement
		return false;
	}

	private boolean committedLocal(ProposeRequest m, int v, int n) {
		// TODO: Implement
		return false;
	}

	/* --------------------------------------- Request Handlers ----------------------------------- */

	private void uponProposeRequest(ProposeRequest req, short sourceProto) {
		logger.info("Received request: " + req);
		view.forEach(h -> sendMessage(new PrePrepareMessage(), h) );
	}

	/* --------------------------------------- Message Handlers ----------------------------------- */

	private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
		// TODO: Implement
	}

	private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {
		// TODO: Implement
	}

	private void uponCommitMessage(CommitMessage msg, Host sender, short sourceProtocol, int channelId) {
		// TODO: Implement
	}

	/* --------------------------------------- Notification Handlers ----------------------------------- */

	/* --------------------------------------- Timer Handlers ----------------------------------- */

	/* --------------------------------------- Connection Manager Functions ----------------------------------- */
	
    private void uponOutConnectionUp(OutConnectionUp event, int channel) {
        logger.info(event);
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channel) {
        logger.warn(event);
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> ev, int ch) {
    	logger.warn(ev); 
    	openConnection(ev.getNode());
    }

    private void uponInConnectionUp(InConnectionUp event, int channel) {
        logger.info(event);
    }

    private void uponInConnectionDown(InConnectionDown event, int channel) {
        logger.warn(event);
    }
	
	/* ----------------------------------------------- ------------- ------------------------------------------ */
    /* ----------------------------------------------- APP INTERFACE ------------------------------------------ */
    /* ----------------------------------------------- ------------- ------------------------------------------ */
    public void submitOperation(byte[] b, byte[] sig) {
		if (primary)
    		sendRequest(new ProposeRequest(b, sig), PBFTProtocol.PROTO_ID);
    }
	
}
