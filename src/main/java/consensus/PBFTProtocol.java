package consensus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import consensus.notifications.CommittedNotification;
import consensus.requests.ProposeRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidFormatException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.babel.generic.signed.NoSignaturePresentException;
import pt.unl.fct.di.novasys.channel.tcp.MultithreadedTCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;

public class PBFTProtocol extends GenericProtocol {

	public static final String PROTO_NAME = "pbft";
	public static final short PROTO_ID = 100;

	public static final String ADDRESS_KEY = "address";
	public static final String PORT_KEY = "base_port";
	public static final String INITIAL_MEMBERSHIP_KEY = "initial_membership";

	private static final Logger logger = LogManager.getLogger(PBFTProtocol.class);

	private String cryptoName;
	private PrivateKey key;
	// cryptoName -> public key
	private final Map<String, PublicKey> publicKeys;

	private int viewN;
	private int seq;
	private int nextToExecute;

	private final Host self;
	private final List<Host> view;
	private String primaryCryptoName;
	private final int f;

	private final List<Checkpoint> unstableCheckpoints;
	private Checkpoint stableCheckpoint;
	private int lowH, highH;

	private final Map<Integer, PrePrepareMessage> prePreparesLog;

	private final Map<Integer, Set<PrepareMessage>> preparesLog;

	// seq -> commit set
	private final Map<Integer, Set<CommitMessage>> commitsLog;

	public PBFTProtocol(Properties props) throws NumberFormatException, UnknownHostException {
		super(PBFTProtocol.PROTO_NAME, PBFTProtocol.PROTO_ID);

		this.publicKeys = new HashMap<>();

		this.seq = 0;
		this.nextToExecute = 0;
		this.viewN = 0;
		this.primaryCryptoName = props.getProperty("bootstrap_primary");
		this.prePreparesLog = new HashMap<>();
		this.preparesLog = new HashMap<>();
		this.commitsLog = new HashMap<>();
		this.unstableCheckpoints = new LinkedList<>();

		// TODO change when checkpointing is implemented
		this.lowH = -1;
		this.highH = Integer.MAX_VALUE;

		this.self = new Host(InetAddress.getByName(props.getProperty(ADDRESS_KEY)),
				Integer.parseInt(props.getProperty(PORT_KEY)));

		this.view = new LinkedList<>();
		String[] membership = props.getProperty(INITIAL_MEMBERSHIP_KEY).split(",");
		for (String s : membership) {
			String[] tokens = s.split(":");
			view.add(new Host(InetAddress.getByName(tokens[0]), Integer.parseInt(tokens[1])));
		}

		this.f = (view.size() - 1) / 3;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		try {
			cryptoName = props.getProperty(Crypto.CRYPTO_NAME_KEY);
			KeyStore truststore = Crypto.getTruststore(props);
			key = Crypto.getPrivateKey(cryptoName, props);
			for (var it = truststore.aliases().asIterator(); it.hasNext();) {
				var name = it.next();
				publicKeys.put(name, truststore.getCertificate(name).getPublicKey());
			}

		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
				| IOException e) {
			throw new RuntimeException(e);
		}

		Properties peerProps = new Properties();
		peerProps.put(MultithreadedTCPChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
		peerProps.setProperty(TCPChannel.PORT_KEY, props.getProperty(PORT_KEY));
		int peerChannel = createChannel(TCPChannel.NAME, peerProps);

		logger.info("Standing by to establish connections (5s)");

		registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);

		registerMessageHandler(peerChannel, PrePrepareMessage.MESSAGE_ID, this::uponPrePrepareMessage);
		registerMessageHandler(peerChannel, PrepareMessage.MESSAGE_ID, this::uponPrepareMessage);
		registerMessageHandler(peerChannel, CommitMessage.MESSAGE_ID, this::uponCommitMessage);

		registerMessageSerializer(peerChannel, PrePrepareMessage.MESSAGE_ID, PrePrepareMessage.serializer);
		registerMessageSerializer(peerChannel, PrepareMessage.MESSAGE_ID, PrepareMessage.serializer);
		registerMessageSerializer(peerChannel, CommitMessage.MESSAGE_ID, CommitMessage.serializer);

		registerChannelEventHandler(peerChannel, InConnectionDown.EVENT_ID, this::uponInConnectionDown);
		registerChannelEventHandler(peerChannel, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(peerChannel, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(peerChannel, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(peerChannel, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);

		try {
			Thread.sleep(5 * 1000);
		} catch (InterruptedException ignored) {
		}

		view.forEach(this::openConnection);
	}

	/*
	 * --------------------------------------- Auxiliary Functions
	 * -----------------------------------
	 */

	private void commitRequests() {
		var request = prePreparesLog.get(nextToExecute).getRequest();
		while (request != null && committed(request, viewN, nextToExecute)) {
			triggerNotification(new CommittedNotification(request.getBlock(), request.getSignature()));
			nextToExecute++;
			request = prePreparesLog.get(nextToExecute).getRequest();
		} 
	}

	/*
	 * --------------------------------------- Predicates
	 * -----------------------------------
	 */

	/*
	 * We define the predicate prepared(m,v,n,i) to be true if and only if replica i
	 * has inserted in its log:
	 * the request m, a pre-prepare for m in view v with sequence number n, and 2f
	 * prepares from different backups
	 * that match the pre-prepare.
	 */
	private boolean prepared(ProposeRequest m, int v, int n) {

		while (preparesLog.get(n).size() < 2 * f) {
			return false;
		}

		PrePrepareMessage prePrepareMessage = prePreparesLog.get(n);

		if(prePrepareMessage == null){
			return false;
		} else if(prePrepareMessage.getRequest() != m || prePrepareMessage.getViewN() != v ){
			return true;
		}

		return false;
	}

	private boolean committed(ProposeRequest m, int v, int n) {
		return prepared(m, v, n) && commitsLog.get(n).size() >= 2 * f + 1;
	}

	/*
	 * --------------------------------------- Request Handlers
	 * -----------------------------------
	 */

	private void uponProposeRequest(ProposeRequest req, short sourceProto) {
		logger.info("Received request: " + req);
		var prepareMessage = new PrepareMessage(viewN, seq, req.getDigest(), cryptoName);
		var prePrepareMessage = new PrePrepareMessage(viewN, seq, req.getDigest(), req);
		prePreparesLog.put(seq, prePrepareMessage);
		preparesLog.computeIfAbsent(seq, k -> new HashSet<>()).add(prepareMessage);
		try {
			prePrepareMessage.signMessage(key);
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
			throw new RuntimeException(e);
		}
		view.forEach(h -> sendMessage(prePrepareMessage, h));
	}

	/*
	 * --------------------------------------- Message Handlers -----------------------------------
	 */

	private void uponPrePrepareMessage(PrePrepareMessage msg, Host sender, short sourceProtocol, int channelId) {

		try {

			if (prePreparesLog.containsKey(msg.getSeq())) {
				logger.warn("PrePrepareMessage already exists.");
				return;
			}

			if (Arrays.equals(msg.getDigest(), msg.getRequest().getDigest())) {
				logger.warn("Digests don't match.");
				return;

			}

			if (!msg.checkSignature(publicKeys.get(primaryCryptoName))) {
				logger.warn("Invalid signature.");
				return;
			}

		} catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException e) {
			logger.warn(e.getMessage());
			return;
		}

		prePreparesLog.put(seq, msg);

		var prepareMessage = new PrepareMessage(msg, cryptoName);
		try {
			prepareMessage.signMessage(key);
		} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
			throw new RuntimeException(e);
		}

		view.forEach(h -> {
			sendMessage(prepareMessage, h); 
		});

	}

	private void uponPrepareMessage(PrepareMessage msg, Host sender, short sourceProtocol, int channelId) {

		ProposeRequest request = prePreparesLog.get(nextToExecute).getRequest();

		if(msg.getViewN() == viewN && prepared(request, viewN, seq)){

			preparesLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
			var commitMessage = new CommitMessage(msg, cryptoName);

			try {
				commitMessage.signMessage(key);
			} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
				throw new RuntimeException(e);
			}

			view.forEach(h -> {
				sendMessage(commitMessage, h);
			});
		} else {
			logger.warn("Invalid message.");
			return;
		}

	}

	private void uponCommitMessage(CommitMessage msg, Host sender, short sourceProtocol, int channelId) {
		assert msg.getSeq() < nextToExecute;

		try {
			if (msg.getViewN() == viewN && msg.checkSignature(publicKeys.get(msg.getCryptoName())) && seq > lowH
					&& seq < highH)
				commitsLog.computeIfAbsent(msg.getSeq(), k -> new HashSet<>()).add(msg);
			else {
				logger.warn("Invalid message.");
				return;
			}
		} catch (SignatureException | InvalidFormatException | NoSignaturePresentException | NoSuchAlgorithmException
				| InvalidKeyException e) {
			logger.warn(e.getMessage());
			return;
		}

		commitRequests();
	}

	/*
	 * --------------------------------------- Notification Handlers ------------------------------------------
	 */

	/*
	 * --------------------------------------- Timer Handlers -------------------------------------------------
	 */

	/*
	 * --------------------------------------- Connection Manager Functions -----------------------------------
	 */

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

	/*
	 * ----------------------------------------------- APP INTERFACE ------------------------------------------
	 */
	public void submitOperation(byte[] b, byte[] sig) {
		if (primaryCryptoName.equals(cryptoName))
			sendRequest(new ProposeRequest(b, sig), PBFTProtocol.PROTO_ID);
	}

}
