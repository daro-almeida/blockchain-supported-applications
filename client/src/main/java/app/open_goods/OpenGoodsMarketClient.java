package app.open_goods;

import app.OperationStatusReply;
import app.WriteOperation;
import app.open_goods.messages.client.replies.GenericClientReply;
import app.open_goods.messages.client.requests.Cancel;
import app.open_goods.messages.client.requests.CheckOperationStatus;
import app.open_goods.messages.client.requests.IssueOffer;
import app.open_goods.messages.client.requests.IssueWant;
import app.open_goods.messages.exchange.requests.Deposit;
import app.open_goods.messages.exchange.requests.Withdrawal;
import app.open_goods.timers.ExpiredOperation;
import app.open_goods.timers.NextCheck;
import app.open_goods.timers.NextOperation;
import io.netty.channel.EventLoopGroup;
import metrics.Metrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.babel.generic.signed.InvalidSerializerException;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OpenGoodsMarketClient {

    private static final Logger logger = LogManager.getLogger(OpenGoodsMarketClient.class);
         
    public final static String INTERFACE = "interface";
    public final static String ADDRESS = "address";
    
    public final static String PROTO_NAME = "OpenGoodsMarketClientProto";
    public final static short PROTO_ID = 600;
    
    public final static String INITIAL_PORT = "initial_port";
    public final static String NUMBER_OF_CLIENTS = "clients";
    private short initial_port;
    private final short number_of_clients;

    public final static String APP_SERVER_PROTO = "server_proto";
    private final short application_proto_number;
    
    public final static String REFRESH_TIMER = "check_requests_period";
    public final static String OPERATION_TIMEOUT = "operation_timeout";
    
    public final static String KEY_STORE_FILE = "key_store";
    public final static String EXCHANGE_KEY_STORE_FILE = "ex_key_store";
    public final static String KEY_STORE_PASSWORD = "key_store_password";
    public final static String EXCHANGE_KEY_STORE_PASSWORD = "ex_key_store_password";
    
    public final static String SERVER_LIST = "server_list";

    private final long operation_refresh_timer;
    private final long operation_timeout;
	private final int sendPeriod;
    
    private final KeyStore exchange;
    private final KeyStore keystore;
    
    ExchangeClient exchangeClient;
    ClientInstance[] clients;
    
    private final ConcurrentHashMap<PublicKey, Float> wallets;
    private final String[] items = new String[] {"lettuce", "carrot", "potato", "milk", "chocolate", "bread",
    		"apple", "butter", "egg", "cheese", "toilet paper", "iogurt", "cookie", "knive", "fork", "spoon"};
    private final int[] quantity = new int[] {1, 2, 3, 5, 10, 15, 25, 50, 100, 150, 200, 300, 500};
    private final float[] price = new float[] {(float)0.5,(float)0.7, (float)1, (float)1.1, (float)1.2, (float)1.3, (float)1.5, (float)2.0, (float)2.2, (float)2.5, (float)3.0};
    
    private final Host[] servers;
    
    public final static String OFFER_FRACTION = "offer_fraction";
    public final static String WANT_FRACTION = "want_fraction";
    
   
    private final int offer;
    private final int want;
    
    private enum OPER { OFFER, WANT }

	private OPER[] ops = null;
    
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

		new OpenGoodsMarketClient(props);
    }

	public OpenGoodsMarketClient(Properties props) throws IOException, ProtocolAlreadyExistsException,
            HandlerRegistrationException, GeneralSecurityException {

    	this.initial_port = Short.parseShort(props.getProperty(INITIAL_PORT));
    	this.number_of_clients = Short.parseShort(props.getProperty(NUMBER_OF_CLIENTS));
		var opsSec = Short.parseShort(props.getProperty("ops_sec", "1"));
		this.sendPeriod = Math.max(1000 / opsSec, 1);
    	         
        this.operation_refresh_timer = Long.parseLong(props.getProperty(REFRESH_TIMER));
        this.operation_timeout = Long.parseLong(props.getProperty(OPERATION_TIMEOUT));
    	
        this.application_proto_number = Short.parseShort(props.getProperty(APP_SERVER_PROTO));
        
        this.offer = Integer.parseInt(props.getProperty(OFFER_FRACTION,"5"));
        this.want = Integer.parseInt(props.getProperty(WANT_FRACTION,"5"));

        this.ops = new OPER[offer+want];

        int j = 0;
   	 
	   	 for(; j < offer; j++) {
	   		 ops[j] = OPER.OFFER;
	   	 }
	   	 for(; j < offer+want; j++) {
	   		 ops[j] = OPER.WANT;
	   	 }
        
         String keyStoreLocation = props.getProperty(KEY_STORE_FILE);
         char[] password = props.getProperty(KEY_STORE_PASSWORD).toCharArray();
         String exKeyStoreLocation = props.getProperty(EXCHANGE_KEY_STORE_FILE);
         char[] exPassword = props.getProperty(EXCHANGE_KEY_STORE_PASSWORD).toCharArray();
         
         this.keystore = KeyStore.getInstance(KeyStore.getDefaultType());
         this.exchange = KeyStore.getInstance(KeyStore.getDefaultType());
         
    	 try (FileInputStream fis = new FileInputStream(keyStoreLocation)) {
             this.keystore.load(fis, password);
         }
    	 
    	 try (FileInputStream fis = new FileInputStream(exKeyStoreLocation)) {
    		 this.exchange.load(fis, exPassword);
    	 }
    	     	    	
    	 String servers = props.getProperty(SERVER_LIST);
    	 String[] token = servers.split(",");
    	 ArrayList<Host> hosts = new ArrayList<>();
    	 for(String s: token) {
    		 String[] e = s.split(":");
    		 hosts.add(new Host(InetAddress.getByName(e[0]), Short.parseShort(e[1])));
    	 }
    	 this.servers = hosts.toArray(new Host[0]);
    	 
    	 this.wallets = new ConcurrentHashMap<PublicKey, Float>();
    	 
    	 EventLoopGroup nm = NetworkManager.createNewWorkerGroup();
    	 
    	 this.b = Babel.getInstance();
    	 
    	 this.exchangeClient = new ExchangeClient(this.initial_port, exPassword, nm, b);
    	 this.clients = new ClientInstance[this.number_of_clients];
    	 for(short i = 1; i <= this.number_of_clients; i++) {
    		 this.initial_port += this.servers.length;
    		 this.clients[i-1] = new ClientInstance(i, this.initial_port, password, nm, b);
    	 }
    	
    	 this.exchangeClient.init(props);
    	 for(short i = 0; i < this.number_of_clients; i++) {
    		 //System.err.println("Initializing client: " + this.clients[i].client_name);
    		 this.clients[i].init(props);
    	 }
    	 
    	 //System.err.println("Starting Babel.");
    	 this.b.start();
    	 
    	 try {
    		//System.err.println("Executing Initial Deposits of CSDs.");
			this.exchangeClient.makeInitialDeposit();
			//System.err.println("Completed the Initial Deposits of CSDs.");
    	 } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | InvalidSerializerException e) {
			e.printStackTrace();
			System.exit(3);
    	 }
    }
    
    
    protected class ExchangeClient extends GenericProtocol {
    	private final String client_name;
    	@SuppressWarnings("unused")
		private final PublicKey identity;
    	private final PrivateKey key;
    	private int[] clientChannel;    
    	
    	private final Babel b;
    	    	
    	private short lastServerUsed;
  
    	private final Random r;
    	
    	private final ArrayList<PublicKey> clientKeys;
    	
    	private final EventLoopGroup nm;

		private final HashMap<UUID,Long> pending;

    	public ExchangeClient(short port, char[] password, EventLoopGroup nm, Babel b) throws KeyStoreException, ProtocolAlreadyExistsException, UnrecoverableKeyException, NoSuchAlgorithmException {
    		super(OpenGoodsMarketClient.PROTO_NAME, OpenGoodsMarketClient.PROTO_ID);
			this.client_name = "exchange";
			this.identity = exchange.getCertificate(client_name).getPublicKey();
			this.key = (PrivateKey) exchange.getKey(this.client_name, password);
			this.nm = nm;
			
			this.b = b;
			this.b.registerProtocol(this);		
			
			this.lastServerUsed = 0;
			
			r = new Random(System.currentTimeMillis());
			
			this.clientKeys = new ArrayList<>();

			this.pending = new HashMap<>();
    	}

		@Override
		public void init(Properties props) throws HandlerRegistrationException, IOException {
			clientChannel = new int[servers.length];
			
	    	for(int i = 0; i < clientChannel.length; i++) {
	    	
	    		Properties clientProps2 = new Properties();
	    		clientProps2.put(SimpleClientChannel.WORKER_GROUP_KEY, nm);
	    		clientProps2.put(SimpleClientChannel.ADDRESS_KEY, servers[i].getAddress().getHostAddress());
	    		clientProps2.put(SimpleClientChannel.PORT_KEY, String.valueOf(servers[i].getPort()));
	    		clientChannel[i] = createChannel(SimpleClientChannel.NAME, clientProps2);

		    	registerMessageSerializer(clientChannel[i], CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);

		    	registerMessageSerializer(clientChannel[i], Deposit.MESSAGE_ID, WriteOperation.serializer);
		    	registerMessageSerializer(clientChannel[i], Withdrawal.MESSAGE_ID, WriteOperation.serializer);

		    	registerMessageSerializer(clientChannel[i], OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
		    	registerMessageSerializer(clientChannel[i], GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);

		    	registerMessageHandler(clientChannel[i], GenericClientReply.MESSAGE_ID, this::handleGenericClientReplyMessage);
				registerMessageHandler(clientChannel[i], OperationStatusReply.MESSAGE_ID, this::handleOperationStatusReplyMessage);

		    	registerChannelEventHandler(clientChannel[i], ServerDownEvent.EVENT_ID, this::uponServerDown);
		    	registerChannelEventHandler(clientChannel[i], ServerUpEvent.EVENT_ID, this::uponServerUp);
		    	registerChannelEventHandler(clientChannel[i], ServerFailedEvent.EVENT_ID, this::uponServerFailed);
		       
		    	//System.err.println("Exchange opening connection to " + servers[i] + " on channel " + clientChannel[i]);
		        openConnection(servers[i], clientChannel[i]);
	    	}
			registerTimerHandler(NextOperation.TIMER_ID, this::uponNextOperationTimer);
		}

		private void uponNextOperationTimer(NextOperation t, long id) {
			if (clientKeys.isEmpty()) {
				clientKeys.addAll(wallets.keySet());
			}
			deposit(clientKeys.remove(r.nextInt(clientKeys.size())));
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
		
		public void makeInitialDeposit( ) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, InvalidSerializerException {
			for(PublicKey client: wallets.keySet()) {
				deposit(client);
			}
			for(short i = 0; i < number_of_clients; i++) {
				clients[i].beginSendingOperations();
			}
			setupPeriodicTimer(new NextOperation(), sendPeriod, sendPeriod);
		}

		public void handleOperationStatusReplyMessage(OperationStatusReply osr, Host from, short sourceProto, int channelID ) {
			if(this.pending.containsKey(osr.getrID())) {
				long time = System.currentTimeMillis();
				switch (osr.getStatus()) {
					case REJECTED ->
							Metrics.writeMetric("operation_rejected", "latency", Long.toString(time - pending.remove(osr.getrID())));
					case FAILED ->
							Metrics.writeMetric("operation_failed", "latency", Long.toString(time - pending.remove(osr.getrID())));
					case EXECUTED ->
							Metrics.writeMetric("operation_executed", "latency", Long.toString(time - pending.remove(osr.getrID())));

					default -> {
					}
				}
			} //Else nothing to be done
		}

		public void handleGenericClientReplyMessage(GenericClientReply gcr, Host from, short sourceProto, int channelID ) {
			if(this.pending.containsKey(gcr.getrID())) {
				long time = System.currentTimeMillis();
				Metrics.writeMetric("operation_reply", "latency", Long.toString(time - pending.get(gcr.getrID())));
			} //Else nothing to be done
		}

		private void deposit(PublicKey client) {
			try {
				Deposit d = new Deposit(client, 2000);
				d.signMessage(key);

				sendMessage(clientChannel[lastServerUsed], d, application_proto_number, servers[this.lastServerUsed], 0);
				pending.put(d.getRid(), System.currentTimeMillis());

				lastServerUsed++;
				if(lastServerUsed == servers.length)
					lastServerUsed = 0;
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(2);
			}
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
    	
    	private final Random r;

    	private final EventLoopGroup nm;
    	
		public ClientInstance(short client_id, short port, char[] password, EventLoopGroup nm, Babel b) throws KeyStoreException, ProtocolAlreadyExistsException, UnrecoverableKeyException, NoSuchAlgorithmException {
			super(OpenGoodsMarketClient.PROTO_NAME + client_id, (short) (OpenGoodsMarketClient.PROTO_ID + client_id));
			this.client_id = client_id;
			this.client_name = "client" + this.client_id;
			this.identity = keystore.getCertificate(client_name).getPublicKey();
			this.key = (PrivateKey) keystore.getKey(this.client_name, password);
			this.nm = nm;
			
			this.b = b;
			this.b.registerProtocol(this);	
			
			this.pending = new HashMap<UUID,Long>();
			
			wallets.put(this.identity, 0F);
			
			this.r = new Random(System.currentTimeMillis());
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
		    	
		    	registerMessageSerializer(clientChannel[i], IssueOffer.MESSAGE_ID, WriteOperation.serializer);
		    	registerMessageSerializer(clientChannel[i], IssueWant.MESSAGE_ID, WriteOperation.serializer);
		    	registerMessageSerializer(clientChannel[i], Cancel.MESSAGE_ID, WriteOperation.serializer);
		    	registerMessageSerializer(clientChannel[i], CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);
		    		
		    	registerMessageSerializer(clientChannel[i], OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
		    	registerMessageSerializer(clientChannel[i], GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);
		    	
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
				long time = System.currentTimeMillis();
				switch(osr.getStatus()) {
				case REJECTED:
					Metrics.writeMetric("operation_rejected", "latency", Long.toString(time - pending.remove(osr.getrID())));
					break;
				case FAILED:
					Metrics.writeMetric("operation_failed", "latency", Long.toString(time - pending.remove(osr.getrID())));
					break;
				case EXECUTED:
					Metrics.writeMetric("operation_executed", "latency", Long.toString(time - pending.remove(osr.getrID())));
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
				Metrics.writeMetric("operation_reply", "latency", Long.toString(time - pending.get(gcr.getrID())));
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
				OPER op = ops[r.nextInt(ops.length)];

				SignedProtoMessage operation = null;
				UUID id = null;
				Float f;

				switch (op) {
					case OFFER -> {
						IssueOffer o1 = new IssueOffer(identity, items[r.nextInt(items.length)], quantity[r.nextInt(quantity.length)], price[r.nextInt(price.length)]);
						id = o1.getRid();
						f = wallets.get(identity);
						wallets.put(identity, (f == null ? 0 : f) + o1.getPricePerUnit() * o1.getQuantity());
						operation = o1;
						operation.signMessage(key);
					}
					case WANT -> {
						IssueWant o2 = new IssueWant(identity, items[r.nextInt(items.length)], quantity[r.nextInt(quantity.length)], price[r.nextInt(price.length)]);
						id = o2.getRid();
						operation = o2;
						operation.signMessage(key);
						f = wallets.get(identity);
						float newValue = (f == null ? 0 : f) - o2.getPricePerUnit() * o2.getQuantity();
						if (newValue < 0) return;
						wallets.put(identity, newValue);
					}
				}

				this.pending.put(id, System.currentTimeMillis());
				sendMessage(this.myPrimaryChannel, operation, application_proto_number, this.myPrimaryServer, 0);
				setupTimer(new NextCheck(id), operation_refresh_timer);
				setupTimer(new ExpiredOperation(id, operation), operation_timeout);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

		}

		public void beginSendingOperations() {
			setupPeriodicTimer(new NextOperation(), 0, sendPeriod);
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
