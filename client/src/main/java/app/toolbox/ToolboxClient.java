package app.toolbox;

import app.OperationStatusReply;
import app.WriteOperation;
import app.open_goods.messages.client.replies.GenericClientReply;
import app.open_goods.messages.client.requests.CheckOperationStatus;
import app.open_goods.timers.ExpiredOperation;
import app.open_goods.timers.NextCheck;
import app.open_goods.timers.NextOperation;
import app.toolbox.messages.*;
import io.netty.channel.EventLoopGroup;
import metrics.Metrics;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.distribution.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleClientChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerFailedEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ServerUpEvent;
import pt.unl.fct.di.novasys.network.NetworkManager;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolboxClient {

    private static final Logger logger = LogManager.getLogger(ToolboxClient.class);

    public final static String INTERFACE = "interface";
    public final static String ADDRESS = "address";

    public final static String PROTO_NAME = "ToolboxClient";
    public final static short PROTO_ID = 700;

    public final static String INITIAL_PORT = "initial_port";
    public final static String NUMBER_OF_CLIENTS = "clients";

	public final static String APP_SERVER_PROTO = "server_proto";
    private final short application_proto_number;

    public final static String REFRESH_TIMER = "check_requests_period";
    public final static String OPERATION_TIMEOUT = "operation_timeout";

    public final static String KEY_STORE_FILE = "key_store";
    public final static String KEY_STORE_PASSWORD = "key_store_password";

    public final static String SERVER_LIST = "server_list";

    private final long operation_refresh_timer;
    private final long operation_timeout;
	private final int sendPeriod;

	private final float createPollChance;

	// Object = Distribution
	private final Map<UUID, Pair<Poll, Object>> openPolls = new ConcurrentHashMap<>();
	private final Map<UUID, Integer > numberVotes = new ConcurrentHashMap<>();

    private final KeyStore keystore;

    ClientInstance[] clients;

	DiscretePollValues[] discretePollValues = {
			new DiscretePollValues(
					"Favorite Pizza Topping",
					Set.of("Spinach", "Pineapple", "Green peppers", "Black olives", "Extra cheese", "Bacon", "Sausage", "Pepperoni", "Mushrooms", "Onions"),
					Poll.Authorization.OPEN,
					new BinomialDistribution(9, 0.7)),
			new DiscretePollValues(
					"Annual Income Bracket",
					Set.of("Less than $25,000", "$25,000 to $49,999", "$50,000 to $74,999", "$75,000 to $99,999", "$100,000 to $124,999", "$125,000 to $149,999", "$150,000 or more"),
					Poll.Authorization.OPEN,
					new BinomialDistribution(6, 0.2)),
	};

	NumericPollValues[] numericPollValues = {
			new NumericPollValues(
					"Final Grade in CSD",
					0.0, 20.0,
					Poll.Authorization.OPEN,
					new UniformRealDistribution(8.0, 20.0)),
			new NumericPollValues(
					"Satisfaction Level with Current Job at NOVA SST",
					0.0, 10.0,
					Poll.Authorization.OPEN,
					new NormalDistribution(8.0, 0.5)),
	};

    private final Host[] servers;

    private final Babel b;

    public static void main(String[] args) throws InvalidParameterException, IOException,
            HandlerRegistrationException, ProtocolAlreadyExistsException, GeneralSecurityException {
        Properties props =
                Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);

        if (props.containsKey(INTERFACE)) {
            String address = getAddress(props.getProperty(INTERFACE));
            if (address == null) return;
            props.put(ADDRESS, address);
		}

		Metrics.initMetrics(props);

		new ToolboxClient(props);
    }

	public ToolboxClient(Properties props) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, ProtocolAlreadyExistsException, HandlerRegistrationException {

		short initial_port = Short.parseShort(props.getProperty(INITIAL_PORT));
		short number_of_clients = Short.parseShort(props.getProperty(NUMBER_OF_CLIENTS));
		var opsSec = Short.parseShort(props.getProperty("ops_sec", "1"));
		this.sendPeriod = Math.max(1000 / opsSec, 1);

		this.operation_refresh_timer = Long.parseLong(props.getProperty(REFRESH_TIMER));
		this.operation_timeout = Long.parseLong(props.getProperty(OPERATION_TIMEOUT));

		this.application_proto_number = Short.parseShort(props.getProperty(APP_SERVER_PROTO));
		this.createPollChance = Float.parseFloat(props.getProperty("create_poll_chance", "0.0"));

		String keyStoreLocation = props.getProperty(KEY_STORE_FILE);
		char[] password = props.getProperty(KEY_STORE_PASSWORD).toCharArray();
		this.keystore = KeyStore.getInstance(KeyStore.getDefaultType());

		try (FileInputStream fis = new FileInputStream(keyStoreLocation)) {
			this.keystore.load(fis, password);
		}

		String servers = props.getProperty(SERVER_LIST);
		String[] token = servers.split(",");
		ArrayList<Host> hosts = new ArrayList<>();
		for(String s: token) {
		 String[] e = s.split(":");
		 hosts.add(new Host(InetAddress.getByName(e[0]), Short.parseShort(e[1])));
		}
		this.servers = hosts.toArray(new Host[0]);

		EventLoopGroup nm = NetworkManager.createNewWorkerGroup();

		this.b = Babel.getInstance();

		this.clients = new ClientInstance[number_of_clients];
		for(short i = 1; i <= number_of_clients; i++) {
		 initial_port += this.servers.length;
		 this.clients[i-1] = new ClientInstance(i, initial_port, password, nm, b);
		}

		for(short i = 0; i < number_of_clients; i++) {
		 //System.err.println("Initializing client: " + this.clients[i].client_name);
			this.clients[i].init(props);
		}

		this.b.start();

		try {
			Thread.sleep(2000);
			this.clients[0].createInitialPolls();
		} catch (InvalidSerializerException | SignatureException | InvalidKeyException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		for(short i = 0; i < number_of_clients; i++) {
			this.clients[i].beginSendingOperations();
		}

    }

    protected class ClientInstance extends GenericProtocol {

    	private final short client_id;
    	private final String client_name;
    	private final PublicKey identity;
    	private final PrivateKey key;
    	private int[] clientChannel;
    		
    	private Host myPrimaryServer;
    	private int myPrimaryChannel;
    	
    	private final Babel b;
    	private final HashMap<UUID,Long> pending;
    	private final EventLoopGroup nm;
		private final Random rand = new Random(System.currentTimeMillis());

		public ClientInstance(short client_id, short port, char[] password, EventLoopGroup nm, Babel b) throws KeyStoreException, ProtocolAlreadyExistsException, UnrecoverableKeyException, NoSuchAlgorithmException {
			super(ToolboxClient.PROTO_NAME + client_id, (short) (ToolboxClient.PROTO_ID + client_id));
			this.client_id = client_id;
			this.client_name = "client" + this.client_id;
			this.identity = keystore.getCertificate(client_name).getPublicKey();
			this.key = (PrivateKey) keystore.getKey(this.client_name, password);
			this.nm = nm;
			
			this.b = b;
			this.b.registerProtocol(this);	
			
			this.pending = new HashMap<>();
		}
		
		@Override
		public void init(Properties props) throws HandlerRegistrationException, IOException {
			clientChannel = new int[servers.length];
			
	    	for(int i = 0; i < servers.length; i++) {
	    		
	    		Properties clientProps2 = new Properties();
	    		clientProps2.put(SimpleClientChannel.WORKER_GROUP_KEY, nm);
	    		clientProps2.put(SimpleClientChannel.ADDRESS_KEY, servers[i].getAddress().getHostAddress());
	    		clientProps2.put(SimpleClientChannel.PORT_KEY, String.valueOf(servers[i].getPort()));
				clientChannel[i] = createChannel(SimpleClientChannel.NAME, clientProps2);
		    	
		    	registerMessageSerializer(clientChannel[i], CreatePoll.MESSAGE_ID, WriteOperation.serializer);
		    	registerMessageSerializer(clientChannel[i], Vote.MESSAGE_ID, WriteOperation.serializer);
				registerMessageSerializer(clientChannel[i], ClosePoll.MESSAGE_ID, WriteOperation.serializer);
		    		
		    	registerMessageSerializer(clientChannel[i], OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
		    	registerMessageSerializer(clientChannel[i], GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);
				registerMessageSerializer(clientChannel[i], CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);
		    	
		    	registerMessageHandler(clientChannel[i], OperationStatusReply.MESSAGE_ID, this::handleOperationStatusReplyMessage);
		    	registerMessageHandler(clientChannel[i], GenericClientReply.MESSAGE_ID, this::handleGenericClientReplyMessage);
		    	
		    	registerChannelEventHandler(clientChannel[i], ServerDownEvent.EVENT_ID, this::uponServerDown);
		    	registerChannelEventHandler(clientChannel[i], ServerUpEvent.EVENT_ID, this::uponServerUp);
		    	registerChannelEventHandler(clientChannel[i], ServerFailedEvent.EVENT_ID, this::uponServerFailed);
		       
		    	//System.err.println("Client " + client_name + " opening connection to " + servers[i] + " on channel " + clientChannel[i]);
		        openConnection(servers[i], clientChannel[i]);
			}

			registerTimerHandler(NextOperation.TIMER_ID, this::handleNextOperationTimer);
	    	registerTimerHandler(NextCheck.TIMER_ID, this::handleNextCheckTimer);
	    	registerTimerHandler(ExpiredOperation.TIMER_ID, this::handleExpiredOperationTimer);
	    		    
	    	this.myPrimaryChannel = clientChannel[client_id % servers.length];
	    	this.myPrimaryServer = servers[client_id % servers.length];
		}

		private void handleNextOperationTimer(NextOperation t, long id) {
			sendOperation();
		}

		private void uponServerDown(ServerDownEvent event, int channel) {
			logger.warn(client_name + " " + event);
		}
		
		private void uponServerUp(ServerUpEvent event, int channel) {
			 logger.debug(client_name + " " + event);
		}
		
		private void uponServerFailed(ServerFailedEvent event, int channel) {
			logger.warn(client_name + " " + event); 
		}
		
		public void handleOperationStatusReplyMessage(OperationStatusReply osr, Host from, short sourceProto, int channelID ) {
			if(this.pending.containsKey(osr.getrID())) {
				switch(osr.getStatus()) {
				case REJECTED:
					//Metrics.writeMetric("operation_rejected", "latency", Long.toString(time - pending.remove(osr.getrID())));
					break;
				case FAILED:
					//Metrics.writeMetric("operation_failed", "latency", Long.toString(time - pending.remove(osr.getrID())));
					break;
				case EXECUTED:
					//Metrics.writeMetric("operation_executed", "latency", Long.toString(time - pending.remove(osr.getrID())));
					break;
				case CANCELLED:
					//Should never happen
					break;
				case PENDING:
				case UNKNOWN:
				default:
					setupTimer(new NextCheck(osr.getrID()), operation_refresh_timer);
					break;
				
				}
			} //Else nothing to be done
		}
		
		public void handleGenericClientReplyMessage(GenericClientReply gcr, Host from, short sourceProto, int channelID ) {
			if(this.pending.containsKey(gcr.getrID())) {
				long time = System.currentTimeMillis();
				//Metrics.writeMetric("operation_reply", "latency", Long.toString(time - pending.get(gcr.getrID())));
			} //Else nothing to be done
		}

		public void handleNextCheckTimer(NextCheck nc, long delay) {
			if(!this.pending.containsKey(nc.req)) return;
			
			CheckOperationStatus cos = new CheckOperationStatus(nc.req);
			sendMessage(this.myPrimaryChannel, cos, application_proto_number, this.myPrimaryServer, 0);
		}

		public void handleExpiredOperationTimer(ExpiredOperation eo, long delay) {
			if(!this.pending.containsKey(eo.req)) return;

			logger.warn("Operation " + eo.req + " expired");
			for(int i = 0; i < servers.length; i++) {
				sendMessage(clientChannel[i], eo.message, application_proto_number, servers[i], 0);
			}
		}

		public void sendOperation() {
			try {
				WriteOperation op = null;
				var canVoteOn = canVote();
				var id = UUID.randomUUID();
				if ((canVoteOn.isEmpty() && createPollChance > 0.0) || rand.nextFloat() <= createPollChance) {
					Poll poll;
					if (rand.nextFloat() < 0.5) {
						var pollValues = discretePollValues[rand.nextInt(discretePollValues.length)];

						if (pollValues.authorization() == Poll.Authorization.OPEN)
							poll = new DiscretePoll(pollValues.description(), clients.length, pollValues.values());
						else {
							var authorized = createAuthorizedSet(clients.length);
							poll = new DiscretePoll(pollValues.description(), authorized.size(), authorized, pollValues.values());
						}
						openPolls.put(id, Pair.of(poll, pollValues.voteDistribution()));
					} else {
						var pollValues = numericPollValues[rand.nextInt(numericPollValues.length)];

						if (pollValues.authorization() == Poll.Authorization.OPEN)
							poll = new NumericPoll(pollValues.description(), clients.length, pollValues.min(), pollValues.max());
						else {
							var authorized = createAuthorizedSet(clients.length);
							poll = new NumericPoll(pollValues.description(), authorized.size(), authorized, pollValues.min(), pollValues.max());
						}
						openPolls.put(id, Pair.of(poll, pollValues.voteDistribution()));
					}
					numberVotes.put(id, 0);

					Metrics.writeMetric("poll_create", "pollId", id.toString(), "type", poll.getType().toString(), "description", poll.getDescription());
					op = new CreatePoll(id, identity, poll);
				} else if (!canVoteOn.isEmpty()) {
					var entry = canVoteOn.get(rand.nextInt(canVoteOn.size()));
					var pollId = entry.getKey();
					var poll = entry.getValue().getKey();

					switch (poll.getType()) {
						case DISCRETE -> {
							var discretePoll = (DiscretePoll) poll;
							var voteDistribution = (AbstractIntegerDistribution) entry.getValue().getValue();
							var trueVoteValue =  voteDistribution.sample();
							op = new DiscreteVote(id, identity, pollId, trueVoteValue);
							assert discretePoll.validVote((Vote<?>) op);
							//TODO noisyVoteValue = ...

							Metrics.writeMetric("poll_vote", "pollId", pollId.toString(), "trueVoteValue",
									Integer.toString(trueVoteValue), "noisyVoteValue", "");

						}
						case NUMERIC -> {
							var numericPoll = (NumericPoll) poll;
							var voteDistribution = (AbstractRealDistribution) entry.getValue().getValue();
							double voteValue;
							do {
								voteValue = voteDistribution.sample();
								op = new NumericVote(id, identity, pollId, voteValue);
							} while (!numericPoll.validVote((Vote<?>) op));
							//TODO noisyVoteValue = ...
							Metrics.writeMetric("poll_vote", "pollId", pollId.toString(), "trueVoteValue",
									Double.toString(voteValue), "noisyVoteValue", "");
						}
						default -> throw new IllegalStateException("Unexpected poll type value: " + poll.getType());
					}

					if (!numberVotes.containsKey(pollId))
						return;

					if (numberVotes.get(pollId) >= poll.getMaxParticipants()) {
						Metrics.writeMetric("poll_complete", "pollId", pollId.toString(), "numVotes",
								Integer.toString(numberVotes.get(pollId)));

						openPolls.remove(pollId);
						numberVotes.remove(pollId);
					}
					else
						numberVotes.put(pollId, numberVotes.get(pollId) + 1);
				}
				if (op == null)
					return;
				this.pending.put(id, System.currentTimeMillis());
				op.signMessage(key);
				sendMessage(this.myPrimaryChannel, op, application_proto_number, this.myPrimaryServer, 0);
				setupTimer(new NextCheck(id), operation_refresh_timer);
				setupTimer(new ExpiredOperation(id, op), operation_timeout);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		private Set<PublicKey> createAuthorizedSet(int max) {
			var authorized = new HashSet<PublicKey>();
			authorized.add(identity);
			for (int i = 0; i < rand.nextInt(max/2 + 1, max - 1); i++) {
				authorized.add(clients[rand.nextInt(clients.length)].identity);
			}

			return authorized;
		}

		private List<Map.Entry<UUID, Pair<Poll, Object>>> canVote() {
			return openPolls.entrySet().stream().filter(e -> e.getValue().getKey().canVote(identity)).toList();
		}

		public void beginSendingOperations() {
			setupPeriodicTimer(new NextOperation(), 0, sendPeriod);
		}

		public void createInitialPolls() throws InvalidSerializerException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
			for (var pollValues: numericPollValues ) {
				var id = UUID.randomUUID();
				NumericPoll poll;
				if (pollValues.authorization() == Poll.Authorization.OPEN) {
					poll = new NumericPoll(pollValues.description(), clients.length, pollValues.min(), pollValues.max());
				} else {
					var authorized = createAuthorizedSet(clients.length);
					poll = new NumericPoll(pollValues.description(), authorized.size(), authorized, pollValues.min(), pollValues.max());
				}
				openPolls.put(id, Pair.of(poll, pollValues.voteDistribution()));
				numberVotes.put(id,0);

				this.pending.put(id, System.currentTimeMillis());
				Metrics.writeMetric("poll_create", "pollId", id.toString(), "type", poll.getType().toString(), "description", poll.getDescription());
				var op = new CreatePoll(id, identity, poll);
				op.signMessage(key);
				sendMessage(this.myPrimaryChannel, op, application_proto_number, this.myPrimaryServer, 0);
				setupTimer(new NextCheck(id), operation_refresh_timer);
				setupTimer(new ExpiredOperation(id, op), operation_timeout);
			}

			for (var pollValues: discretePollValues ) {
				var id = UUID.randomUUID();
				DiscretePoll poll;
				if (pollValues.authorization() == Poll.Authorization.OPEN) {
					poll = new DiscretePoll(pollValues.description(), clients.length, pollValues.values());
				} else {
					var authorized = createAuthorizedSet(clients.length);
					poll = new DiscretePoll(pollValues.description(), authorized.size(), authorized, pollValues.values());
				}
				openPolls.put(id, Pair.of(poll, pollValues.voteDistribution()));
				numberVotes.put(id,0);

				this.pending.put(id, System.currentTimeMillis());
				Metrics.writeMetric("poll_create", "pollId", id.toString(), "type", poll.getType().toString(), "description", poll.getDescription());
				var op = new CreatePoll(id, identity, poll);
				op.signMessage(key);
				sendMessage(this.myPrimaryChannel, op, application_proto_number, this.myPrimaryServer, 0);
				setupTimer(new NextCheck(id), operation_refresh_timer);
				setupTimer(new ExpiredOperation(id, op), operation_timeout);
			}
		}
	}


	private static String getAddress(String inter) throws SocketException {
        NetworkInterface byName = NetworkInterface.getByName(inter);
        if (byName == null) {
            logger.error("No interface named " + inter);
            return null;
        }
        Enumeration<InetAddress> addresses = byName.getInetAddresses();
        InetAddress currentAddress;
        while (addresses.hasMoreElements()) {
            currentAddress = addresses.nextElement();
            if (currentAddress instanceof Inet4Address)
                return currentAddress.getHostAddress();
        }
        logger.error("No ipv4 found for interface " + inter);
        return null;
    }
	
}
