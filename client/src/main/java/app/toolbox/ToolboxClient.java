package app.toolbox;

import app.OperationStatusReply;
import app.WriteOperation;
import app.open_goods.messages.client.replies.GenericClientReply;
import app.open_goods.messages.client.requests.CheckOperationStatus;
import app.timers.ExpiredOperation;
import app.timers.NextCheck;
import app.timers.NextOperation;
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

import java.io.FileInputStream;
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

	private final double createPollChance;
	private final double epsilon;
	private final double delta;
	private final double maxDiffVotes; // for discrete polls

	// Object = Distribution
	private final Map<UUID, Pair<Poll, Object>> openPolls = new ConcurrentHashMap<>();
	private final Map<UUID, Set<Short>> voted = new ConcurrentHashMap<>();

    private final KeyStore keystore;

    ClientInstance[] clients;

	DiscretePollValues[] discretePollValues = {
			new DiscretePollValues(
					"D01",
					Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
					Poll.Authorization.OPEN,
					new UniformIntegerDistribution(0, 9)),
			new DiscretePollValues(
					"D02",
					Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
					Poll.Authorization.OPEN,
					new BinomialDistribution(9, 0.3)),
	};

	NumericPollValues[] numericPollValues = {
			new NumericPollValues(
					"N01",
					0.0, 10.0,
					Poll.Authorization.OPEN,
					new NormalDistribution(5.0, 1.5)),
			new NumericPollValues(
					"N02",
					0.0, 10.0,
					Poll.Authorization.OPEN,
					new NormalDistribution(8.0, 1.5)),
	};

    private final Host[] servers;

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

		this.createPollChance = Double.parseDouble(props.getProperty("create_poll_chance", "0.0"));
		this.epsilon = Double.parseDouble(props.getProperty("epsilon", "1.0"));
		this.delta = Double.parseDouble(props.getProperty("delta", "0.05"));
		this.maxDiffVotes = Double.parseDouble(props.getProperty("max_diff_votes", "0.01"));

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

		Babel b = Babel.getInstance();

		this.clients = new ClientInstance[number_of_clients];
		for(short i = 1; i <= number_of_clients; i++) {
		 initial_port += this.servers.length;
		 this.clients[i-1] = new ClientInstance(i, initial_port, password, nm, b);
		}

		for(short i = 0; i < number_of_clients; i++) {
		 //System.err.println("Initializing client: " + this.clients[i].client_name);
			this.clients[i].init(props);
		}

		b.start();

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

			b.registerProtocol(this);
			
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
//			if(this.pending.containsKey(gcr.getrID())) {
//				long time = System.currentTimeMillis();
//				Metrics.writeMetric("operation_reply", "latency", Long.toString(time - pending.get(gcr.getrID())));
//			} //Else nothing to be done
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

		private double calculateEpsilon(int numExpectedVotes, double sensitivity) {
			if (delta <= 0 || maxDiffVotes <= 0 || sensitivity <= 0) {
				assert epsilon >= 0;
				return epsilon;
			}
			else
				return (sensitivity * Math.log(numExpectedVotes/delta)) / (maxDiffVotes * numExpectedVotes);
		}

		private double calculateTruthProbability(double epsilon) {
			double expPowEpsilon = Math.exp(epsilon);
			return (expPowEpsilon)/(expPowEpsilon + 1);
		}

		private LaplaceDistribution noiseDistribution(double min, double max, double epsilon) {
			return new LaplaceDistribution(0 ,(max-min)/epsilon);
		}

		private DiscretePoll createDiscretePoll(UUID pollId, DiscretePollValues pollValues) {
			DiscretePoll poll;
			int maxParticipants;
			if (pollValues.authorization() == Poll.Authorization.OPEN) {
				maxParticipants = clients.length;
				poll = new DiscretePoll(pollValues.description(), maxParticipants, pollValues.values());
			} else {
				var authorized = createAuthorizedSet(clients.length);
				maxParticipants = authorized.size();
				poll = new DiscretePoll(pollValues.description(), maxParticipants, authorized, pollValues.values());
			}
			var epsilon = calculateEpsilon(maxParticipants, 1.0);
			openPolls.put(pollId, Pair.of(poll, new DiscretePollVotingInfo(pollValues.voteDistribution(),
					calculateTruthProbability(epsilon))));
			voted.put(pollId, ConcurrentHashMap.newKeySet());
			Metrics.writeMetric("poll_create", "pollId", pollId.toString(), "type", poll.getType().toString(),
					"description", poll.getDescription(), "epsilon", Double.toString(epsilon));

			return poll;
		}

		private NumericPoll createNumericPoll(UUID pollId, NumericPollValues pollValues) {
			NumericPoll poll;
			int maxParticipants;
			if (pollValues.authorization() == Poll.Authorization.OPEN) {
				maxParticipants = clients.length;
				poll = new NumericPoll(pollValues.description(), maxParticipants, pollValues.min(), pollValues.max());
			} else {
				var authorized = createAuthorizedSet(clients.length);
				maxParticipants = authorized.size();
				poll = new NumericPoll(pollValues.description(), maxParticipants, authorized, pollValues.min(), pollValues.max());
			}
			//var epsilon = calculateEpsilon(maxParticipants, pollValues.max() - pollValues.min());
			var epsilon = calculateEpsilon(maxParticipants, 0.0);
			openPolls.put(pollId, Pair.of(poll, new NumericPollVotingInfo(pollValues.voteDistribution(),
					noiseDistribution(pollValues.min(), pollValues.max(), epsilon))));
			voted.put(pollId, new HashSet<>());
			Metrics.writeMetric("poll_create", "pollId", pollId.toString(), "type", poll.getType().toString(),
					"description", poll.getDescription(), "epsilon", Double.toString(epsilon));

			return poll;
		}

		public void sendOperation() {
			synchronized (ClientInstance.class) {
				try {
					WriteOperation op;
					var canVoteOn = canVote();
					var id = UUID.randomUUID();
					if ((canVoteOn.isEmpty() && createPollChance > 0.0) || rand.nextDouble() <= createPollChance) {
						Poll poll;
						if (rand.nextDouble() < 0.5) {
							var pollValues = discretePollValues[rand.nextInt(discretePollValues.length)];
							poll = createDiscretePoll(id, pollValues);
						} else {
							var pollValues = numericPollValues[rand.nextInt(numericPollValues.length)];
							poll = createNumericPoll(id, pollValues);
						}

						op = new CreatePoll(id, identity, poll);
					} else if (!canVoteOn.isEmpty()) {
						var entry = canVoteOn.get(rand.nextInt(canVoteOn.size()));
						var pollId = entry.getKey();
						var poll = entry.getValue().getKey();

						switch (poll.getType()) {
							case DISCRETE -> {
								var discretePoll = (DiscretePoll) poll;
								var info = (DiscretePollVotingInfo) entry.getValue().getValue();
								var trueVoteValue = info.voteDistribution().sample();
								int noisyVoteValue;
								if (rand.nextDouble() < info.truthProbability()) {
									noisyVoteValue = trueVoteValue;
								} else {
									noisyVoteValue = rand.nextInt(0, discretePoll.getValues().size() - 1);
									if (noisyVoteValue == trueVoteValue)
										noisyVoteValue = discretePoll.getValues().size() - 1;
								}
								op = new DiscreteVote(id, identity, pollId, noisyVoteValue);
								assert discretePoll.validVote((Vote<?>) op);
								Metrics.writeMetric("poll_vote", "pollId", pollId.toString(), "trueVoteValue",
										Integer.toString(trueVoteValue), "noisyVoteValue", Integer.toString(noisyVoteValue));
							}
							case NUMERIC -> {
								var numericPoll = (NumericPoll) poll;
								var info = (NumericPollVotingInfo) entry.getValue().getValue();
								double voteValue;
								do {
									voteValue = info.voteDistribution().sample();
								} while (voteValue < numericPoll.getMin() || voteValue > numericPoll.getMax());
//								var noisyVoteValue = voteValue + info.noiseDistribution().sample();
//								do {
//									if (noisyVoteValue < numericPoll.getMin())
//										noisyVoteValue += (numericPoll.getMin() - noisyVoteValue) * 2;
//									else if (noisyVoteValue > numericPoll.getMax())
//										noisyVoteValue -= (noisyVoteValue - numericPoll.getMax()) * 2;
//								} while (noisyVoteValue < numericPoll.getMin() || noisyVoteValue > numericPoll.getMax());
								double noisyVoteValue;
								do {
									noisyVoteValue = voteValue + info.noiseDistribution().sample();
								} while (noisyVoteValue < numericPoll.getMin() || noisyVoteValue > numericPoll.getMax());
								op = new NumericVote(id, identity, pollId, noisyVoteValue);
								Metrics.writeMetric("poll_vote", "pollId", pollId.toString(), "trueVoteValue",
										Double.toString(voteValue), "noisyVoteValue", Double.toString(((NumericVote) op).getValue()));
							}
							default -> throw new IllegalStateException("Unexpected poll type value: " + poll.getType());
						}
						voted.get(pollId).add(client_id);

						if (voted.get(pollId).size() == poll.getMaxParticipants()) {
							Metrics.writeMetric("poll_complete", "pollId", pollId.toString(), "numVotes",
									Integer.toString(voted.get(pollId).size()));
							logger.info("Poll complete: {}", pollId);
							openPolls.remove(pollId);
							voted.remove(pollId);
						}
					} else {
						return;
					}

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
			return openPolls.entrySet().stream().filter(e -> voted.containsKey(e.getKey()) &&
					!voted.get(e.getKey()).contains(client_id) &&
					e.getValue().getKey().canVote(identity)).toList();
		}

		public void beginSendingOperations() {
			setupPeriodicTimer(new NextOperation(), 2000, sendPeriod);
		}

		public void createInitialPolls() throws InvalidSerializerException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
			var createPolls = new LinkedList<CreatePoll>();
			for (var pollValues: numericPollValues ) {
				var id = UUID.randomUUID();
				var poll = createNumericPoll(id, pollValues);
				createPolls.add(new CreatePoll(id, identity, poll));
			}

			for (var pollValues: discretePollValues ) {
				var id = UUID.randomUUID();
				var poll = createDiscretePoll(id, pollValues);
				createPolls.add(new CreatePoll(id, identity, poll));
			}

			for (var op : createPolls) {
				var id = op.getRid();
				this.pending.put(id, System.currentTimeMillis());
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
