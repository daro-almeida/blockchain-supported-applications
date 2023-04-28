package app;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.measurements.Measurements;
import app.messages.client.replies.GenericClientReply;
import app.messages.client.replies.OperationStatusReply;
import app.messages.client.requests.Cancel;
import app.messages.client.requests.CheckOperationStatus;
import app.messages.client.requests.IssueOffer;
import app.messages.client.requests.IssueWant;
import app.messages.exchange.requests.Deposit;
import app.messages.exchange.requests.Withdrawal;
import app.timers.ExpiredOperation;
import app.timers.NextCheck;
import app.timers.NextOperation;
import io.netty.channel.EventLoopGroup;
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

public class OpenGoodsMarketClient {

    private static final Logger logger = LogManager.getLogger(OpenGoodsMarketClient.class);
         
    public final static String INTERFACE = "interface";
    public final static String ADDRESS = "address";
    
    public final static String PROTO_NAME = "OpenGoodsMarketClientProto";
    public final static short PROTO_ID = 600;
    
    public final static String INITIAL_PORT = "initial_port";
    public final static String NUMBER_OF_CLIENTS = "clients";
    private short initial_port;
    private short number_of_clients;
    
    public final static String APP_SERVER_PROTO = "server_proto";
    private short application_proto_number;
    
    public final static String REFRESH_TIMER = "check_requests_timeout";
    public final static String OPERATION_TIMEOUT = "operation_timeout";
    
    public final static String KEY_STORE_FILE = "key_store";
    public final static String EXCHANGE_KEY_STORE_FILE = "ex_key_store";
    public final static String KEY_STORE_PASSWORD = "key_store_password";
    public final static String EXCHANGE_KEY_STORE_PASSWORD = "ex_key_store_password";
    
    public final static String SERVER_LIST = "server_list";
    
    public final static String STATS_PERIOD = "report_period";
    private long report_period; //miliseconds;
    
    private long operation_refresh_timer;
    private long operation_timeout;
    
    private KeyStore exchange;
    private KeyStore keystore;
    
    ExchangeClient exchangeClient;
    ClientInstance[] clients;
    
    private ConcurrentHashMap<PublicKey, Float> wallets;
    private String[] items = new String[] {"lettuce", "carrot", "potato", "milk", "chocolate", "bread", 
    		"apple", "butter", "egg", "cheese", "toilet paper", "iogurt", "cookie", "knive", "fork", "spoon"};
    private int[] quantity = new int[] {1, 2, 3, 5, 10, 15, 25, 50, 100, 150, 200, 300, 500};
    private float[] price = new float[] {(float)0.5,(float)0.7, (float)1, (float)1.1, (float)1.2, (float)1.3, (float)1.5, (float)2.0, (float)2.2, (float)2.5, (float)3.0};
    
    private Host[] servers;
    
    public final static String OFFER_FRACTION = "offer_fraction";
    public final static String WANT_FRACTION = "want_fraction";
    
   
    private int offer;
    private int want;
    
    private enum OPER { OFFER, WANT };
    
    private OPER[] ops = null;
    
    private Babel b;
    
    private Measurements m;
    
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
        
        OpenGoodsMarketClient opm = new OpenGoodsMarketClient(props);
        opm.stats();
        
    }

    private long wallclock = 0;
    
    private void stats() {
    	
		while(true) {
			try { Thread.sleep(this.report_period); } catch (Exception e) {} //Every 20 seconds
			
			
			wallclock += this.report_period;
			long pendingOps = 0;
			for(ClientInstance ci: clients) {
				pendingOps += ci.pending.size();
			}
			System.out.println(wallclock + "ms :" + m.getSummary() + " (" + pendingOps + " pending operations)");
		}
	}

	public OpenGoodsMarketClient(Properties props) throws IOException, ProtocolAlreadyExistsException,
            HandlerRegistrationException, GeneralSecurityException {

    	this.initial_port = Short.parseShort(props.getProperty(INITIAL_PORT));
    	this.number_of_clients = Short.parseShort(props.getProperty(NUMBER_OF_CLIENTS));
    	         
        this.operation_refresh_timer = Long.parseLong(props.getProperty(REFRESH_TIMER));
        this.operation_timeout = Long.parseLong(props.getProperty(OPERATION_TIMEOUT));
    	
        this.application_proto_number = Short.parseShort(props.getProperty(APP_SERVER_PROTO));
        
        this.offer = Integer.parseInt(props.getProperty(OFFER_FRACTION,"5"));
        this.want = Integer.parseInt(props.getProperty(WANT_FRACTION,"5"));
        
        this.report_period = Long.parseLong(props.getProperty(STATS_PERIOD));
        
        this.ops = new OPER[offer+want];
        
        this.m = new Measurements(new Properties());
        
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
    	 this.servers = hosts.toArray(new Host[hosts.size()]);
    	 
    	 this.wallets = new ConcurrentHashMap<PublicKey, Float>();
    	 
    	 EventLoopGroup nm = NetworkManager.createNewWorkerGroup();
    	 
    	 this.b = Babel.getInstance();
    	 
    	 this.exchangeClient = new ExchangeClient(this.initial_port, exPassword, nm, b);
    	 this.clients = new ClientInstance[this.number_of_clients];
    	 for(short i = 1; i <= this.number_of_clients; i++) {
    		 this.initial_port += this.servers.length;
    		 this.clients[i-1] = new ClientInstance(i, this.initial_port, password, nm, b, m);
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
    	 
    	 for(short i = 0; i < this.number_of_clients; i++) {
    		 //System.err.println("Starting client: " + this.clients[i].client_name);
    		 this.clients[i].startClient();
    	 }
    }
    
    
    protected class ExchangeClient extends GenericProtocol {
    	private String client_name;
    	@SuppressWarnings("unused")
		private PublicKey identity;
    	private PrivateKey key;
    	private int[] clientChannel;    
    	
    	private Babel b;
    	    	
    	private short lastServerUsed;
  
    	private Random r;
    	
    	private ArrayList<PublicKey> clients;
    	
    	private EventLoopGroup nm;
    	
    	public ExchangeClient(short port, char[] password, EventLoopGroup nm, Babel b) throws KeyStoreException, ProtocolAlreadyExistsException, UnrecoverableKeyException, NoSuchAlgorithmException {
    		super(OpenGoodsMarketClient.PROTO_NAME, (short) (OpenGoodsMarketClient.PROTO_ID));
			this.client_name = "exchange";
			this.identity = exchange.getCertificate(client_name).getPublicKey();
			this.key = (PrivateKey) exchange.getKey(this.client_name, password);
			this.nm = nm;
			
			this.b = b;
			this.b.registerProtocol(this);		
			
			this.lastServerUsed = 0;
			
			r = new Random(System.currentTimeMillis());
			
			this.clients = new ArrayList<>();
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
		    	
		    	registerMessageSerializer(clientChannel[i], Deposit.MESSAGE_ID, Deposit.serializer);
		    	registerMessageSerializer(clientChannel[i], Withdrawal.MESSAGE_ID, Withdrawal.serializer);
		    	
		    	registerMessageSerializer(clientChannel[i], OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
		    	registerMessageSerializer(clientChannel[i], GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);
		    	
		    	registerMessageHandler(clientChannel[i], GenericClientReply.MESSAGE_ID, this::handleGenericClientReplyMessage);
		    	
		    	registerChannelEventHandler(clientChannel[i], ServerDownEvent.EVENT_ID, this::uponServerDown);
		    	registerChannelEventHandler(clientChannel[i], ServerUpEvent.EVENT_ID, this::uponServerUp);
		    	registerChannelEventHandler(clientChannel[i], ServerFailedEvent.EVENT_ID, this::uponServerFailed);
		       
		    	//System.err.println("Exchange opening connection to " + servers[i] + " on channel " + clientChannel[i]);
		        openConnection(servers[i], clientChannel[i]);
	    	}
	    	
	    	registerTimerHandler(NextOperation.TIMER_ID, this::handleNextOperationTimer);
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
			
				Deposit d = new Deposit(client, 2000);
				d.signMessage(key);
				
				sendMessage(clientChannel[lastServerUsed], d, application_proto_number, servers[this.lastServerUsed], 0);
				//System.err.println("Deposited to " + client.toString());
				
				lastServerUsed++;
				if(lastServerUsed == servers.length)
					lastServerUsed = 0;
			}
			
			setupPeriodicTimer(new NextOperation(), 5*1000, 5*1000);
		}
		
		public void handleGenericClientReplyMessage(GenericClientReply gcr, Host from, short sourceProto, int channelID ) {
			//Nothing to be done
		}
		
		public void handleNextOperationTimer(NextOperation no, long delay) {
			//System.err.println("Exchange: Generating new operation...");
			
			try {
				if(clients.isEmpty()) {
					clients.addAll(wallets.keySet());
				}
				
				Deposit d = new Deposit(clients.remove(r.nextInt(clients.size())), 2000);
				d.signMessage(key);
				
				sendMessage(clientChannel[lastServerUsed], d, application_proto_number, servers[this.lastServerUsed], 0);
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

    	private short client_id;
    	private String client_name;
    	private PublicKey identity;
    	private PrivateKey key;
    	private int[] clientChannel;
    		
    	private Host myPrimaryServer;
    	private int myPrimaryChannel;
    	
    	private Babel b;
    	
    	private HashMap<UUID,Long> pending;
    	
    	private Random r;
    	
    	private Measurements m;
    	
    	private EventLoopGroup nm;
    	
		public ClientInstance(short client_id, short port, char[] password, EventLoopGroup nm, Babel b, Measurements m) throws KeyStoreException, ProtocolAlreadyExistsException, UnrecoverableKeyException, NoSuchAlgorithmException {
			super(OpenGoodsMarketClient.PROTO_NAME + client_id, (short) (OpenGoodsMarketClient.PROTO_ID + client_id));
			this.client_id = client_id;
			this.client_name = "client" + this.client_id;
			this.identity = keystore.getCertificate(client_name).getPublicKey();
			this.key = (PrivateKey) keystore.getKey(this.client_name, password);
			this.nm = nm;
			
			this.b = b;
			this.b.registerProtocol(this);	
			
			this.pending = new HashMap<UUID,Long>();
			
			wallets.put(this.identity, new Float(0));
			
			this.r = new Random(System.currentTimeMillis());
			
			this.m = m;
		}

    	public void startClient() {
    		setupPeriodicTimer(new NextOperation(), 10*1000, 10*1000);
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
		    	
		    	registerMessageSerializer(clientChannel[i], IssueOffer.MESSAGE_ID, IssueOffer.serializer);
		    	registerMessageSerializer(clientChannel[i], IssueWant.MESSAGE_ID, IssueWant.serializer);
		    	registerMessageSerializer(clientChannel[i], Cancel.MESSAGE_ID, Cancel.serializer);
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
					m.measure("OPERATION_REJECTED",  (int) (time - pending.remove(osr.getrID())));
					break;
				case FAILED:
					m.measure("OPERATION_FAILED",  (int) (time - pending.remove(osr.getrID())));
					break;
				case EXECUTED:
					m.measure("OPERATION_EXECUTED", (int) (time - pending.remove(osr.getrID())));
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
				m.measure("OPERATION_REPLY", (int) (time - pending.get(gcr.getrID())));
			} //Else nothing to be done
		}
		
		public void handleNextOperationTimer(NextOperation no, long delay) {
			//System.err.println(this.client_name + ": Generating new operation...");
			
			try {
				
				OPER op = ops[r.nextInt(ops.length)];
				
				SignedProtoMessage operation = null;
				UUID id = null;
				Float f = null;
				
				switch(op) {
				case OFFER:
					IssueOffer o1 = new IssueOffer(identity, items[r.nextInt(items.length)], quantity[r.nextInt(quantity.length)], price[r.nextInt(price.length)]);
					id = o1.getRid();
					f = wallets.get(identity);
					wallets.put(identity, (f==null?0:f.floatValue()) + o1.getPricePerUnit() * o1.getQuantity());
					operation = o1;
					operation.signMessage(key);
					break;
				case WANT:
					IssueWant o2 = new IssueWant(identity, items[r.nextInt(items.length)], quantity[r.nextInt(quantity.length)], price[r.nextInt(price.length)]);
					id = o2.getRid();
					operation  = o2;
					operation.signMessage(key);
					f = wallets.get(identity);
					float newValue = (f==null?0:f.floatValue()) - o2.getPricePerUnit() * o2.getQuantity();
					if(newValue < 0) return;
					wallets.put(identity, newValue);
					break;
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
		
		public void handleNextCheckTimer(NextCheck nc, long delay) {
			if(!this.pending.containsKey(nc.req)) return;
			
			CheckOperationStatus cos = new CheckOperationStatus(nc.req);
			sendMessage(this.myPrimaryChannel, cos, application_proto_number, this.myPrimaryServer, 0);
		}

		public void handleExpiredOperationTimer(ExpiredOperation eo, long delay) {
			if(!this.pending.containsKey(eo.req)) return;
			
			for(int i = 0; i < servers.length; i++) {
				sendMessage(clientChannel[i], eo.message, application_proto_number, servers[i], 0);
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
