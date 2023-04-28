package app;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.messages.client.replies.GenericClientReply;
import app.messages.client.replies.OperationStatusReply;
import app.messages.client.replies.OperationStatusReply.Status;
import app.messages.client.requests.Cancel;
import app.messages.client.requests.CheckOperationStatus;
import app.messages.client.requests.IssueOffer;
import app.messages.client.requests.IssueWant;
import app.messages.exchange.requests.Deposit;
import app.messages.exchange.requests.Withdrawal;
import blockchain.BlockChainProtocol;
import consensus.PBFTProtocol;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.exceptions.InvalidParameterException;
import pt.unl.fct.di.novasys.babel.exceptions.ProtocolAlreadyExistsException;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import pt.unl.fct.di.novasys.network.data.Host;

public class OpenGoodsMarket extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(OpenGoodsMarket.class);

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    public final static String PROTO_NAME = "OpenGoodsMarketProto";
    public final static short PROTO_ID = 500;

    private int clientChannel;

    public static void main(String[] args) throws InvalidParameterException, IOException,
            HandlerRegistrationException, ProtocolAlreadyExistsException, GeneralSecurityException {
        Properties props =
                Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);
        if (props.containsKey("interface")) {
            String address = getAddress(props.getProperty("interface"));
            if (address == null) return;
            props.put(ADDRESS_KEY, address);
        }

        Babel babel = Babel.getInstance();

        OpenGoodsMarket opm = new OpenGoodsMarket(props);
        BlockChainProtocol bc = new BlockChainProtocol(props);
        PBFTProtocol pbft = new PBFTProtocol(props);

        babel.registerProtocol(opm);
        babel.registerProtocol(bc);
        babel.registerProtocol(pbft);

        opm.init(props);
        bc.init(props);
        pbft.init(props);

        babel.start();
        logger.info("Babel has started...");

        logger.info("Waiting 10s to start issuing requests.");

        while(true) {
            logger.info("System is running...");
            try {
                Thread.sleep(1000 * 60);
            } catch (InterruptedException e) {
                //Nothing to be done here...
            }
        }

    }

    public OpenGoodsMarket(Properties props) throws IOException, ProtocolAlreadyExistsException,
            HandlerRegistrationException, GeneralSecurityException {

        super(OpenGoodsMarket.PROTO_NAME, OpenGoodsMarket.PROTO_ID);

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

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, props.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);

        registerMessageSerializer(clientChannel, IssueOffer.MESSAGE_ID, IssueOffer.serializer);
        registerMessageSerializer(clientChannel, IssueWant.MESSAGE_ID, IssueWant.serializer);
        registerMessageSerializer(clientChannel, Cancel.MESSAGE_ID, Cancel.serializer);
        registerMessageSerializer(clientChannel, CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);

        registerMessageSerializer(clientChannel, Deposit.MESSAGE_ID, Deposit.serializer);
        registerMessageSerializer(clientChannel, Withdrawal.MESSAGE_ID, Withdrawal.serializer);

        registerMessageSerializer(clientChannel, OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
        registerMessageSerializer(clientChannel, GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);

        registerMessageHandler(clientChannel, IssueOffer.MESSAGE_ID, this::handleIssueOfferMessage);
        registerMessageHandler(clientChannel, IssueWant.MESSAGE_ID, this::handleIssueWantMessage);
        registerMessageHandler(clientChannel, Cancel.MESSAGE_ID, this::handleCancelMessage);
        registerMessageHandler(clientChannel, CheckOperationStatus.MESSAGE_ID, this::handleCheckOperationStatusMessage);

        registerMessageHandler(clientChannel, Deposit.MESSAGE_ID, this::handleDepositMessage);
        registerMessageHandler(clientChannel, Withdrawal.MESSAGE_ID, this::handleWithdrawalMessage);

        registerChannelEventHandler(clientChannel, ClientUpEvent.EVENT_ID, this::uponClientConnectionUp);
        registerChannelEventHandler(clientChannel, ClientDownEvent.EVENT_ID, this::uponClientConnectionDown);
    }


    private HashMap<UUID, OperationStatusReply.Status> opers = new HashMap<>();
    private HashMap<UUID, SignedProtoMessage> opers_body = new HashMap<>();

    public void handleIssueOfferMessage(IssueOffer io, Host from, short sourceProto, int channelID ) {
        logger.info("Received IssueOffer (" + io.getRid() + " from " + from + "(" + io.getcID().toString() + ")");
        opers.put(io.getRid(), OperationStatusReply.Status.PENDING);
        opers_body.put(io.getRid(), io);

        GenericClientReply ack = new GenericClientReply(io.getRid());

        sendMessage(clientChannel, ack, sourceProto, from, 0);
    }

    public void handleIssueWantMessage(IssueWant iw, Host from, short sourceProto, int channelID ) {
        logger.info("Received IssueWant (" + iw.getRid() + " from " + from + "(" + iw.getcID().toString() + ")");
        opers.put(iw.getRid(), OperationStatusReply.Status.PENDING);
        opers_body.put(iw.getRid(), iw);

        GenericClientReply ack = new GenericClientReply(iw.getRid());

        sendMessage(clientChannel, ack, sourceProto, from, 0);
    }

    public void handleCancelMessage(Cancel c, Host from, short sourceProto, int channelID ) {
        logger.info("Received Cancel for operation " + c.getrID() + " from " + from);
        if(opers.containsKey(c.getrID())) {
            opers.put(c.getrID(), OperationStatusReply.Status.CANCELLED);
            opers_body.remove(c.getrID());
        }

        GenericClientReply ack = new GenericClientReply(c.getrID());

        sendMessage(clientChannel, ack, sourceProto, from, 0);
    }

    public void handleCheckOperationStatusMessage(CheckOperationStatus cos, Host from, short sourceProto, int channelID) {
        logger.info("Received CheckOperation for operation " + cos.getrID() + " from " + from);

        OperationStatusReply osr = null;

        Status s = opers.get(cos.getrID());

        if(s != null) {
            switch (s) {
                case CANCELLED:
                    osr = new OperationStatusReply(cos.getrID(), Status.CANCELLED);
                    break;
                case EXECUTED:
                    osr = new OperationStatusReply(cos.getrID(), Status.EXECUTED);
                    break;
                case FAILED:
                    osr = new OperationStatusReply(cos.getrID(), Status.FAILED);
                    break;
                case PENDING:
                    osr = new OperationStatusReply(cos.getrID(), Status.PENDING);
                    int r = new Random(System.currentTimeMillis()).nextInt(100);
                    if(r >= 0 && r < 25) {
                        opers.put(cos.getrID(), Status.EXECUTED);
                    } else if(r >= 25 && r < 50) {
                        opers.put(cos.getrID(), Status.FAILED);
                    } else if(r >= 50 && r < 75) {
                        opers.put(cos.getrID(), Status.REJECTED);
                    } else {
                        opers.put(cos.getrID(), Status.EXECUTED);
                    }
                    break;
                case REJECTED:
                    osr = new OperationStatusReply(cos.getrID(), Status.REJECTED);
                    break;
                default:
                    osr = new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
                    break;

            }
        } else {
            osr = new OperationStatusReply(cos.getrID(), Status.UNKNOWN);
        }

        if(osr != null) {
            sendMessage(clientChannel, osr, sourceProto, from, 0);
        }
    }

    public void handleDepositMessage(Deposit d, Host from, short sourceProto, int channelID) {
        logger.info("Received deposit of " + d.getAmount() + " to " + d.getClientID().toString() + " from the Exchange (" + from + ")");
        opers.put(d.getRid(), OperationStatusReply.Status.PENDING);
        opers_body.put(d.getRid(), d);

        GenericClientReply ack = new GenericClientReply(d.getRid());

        sendMessage(clientChannel, ack, sourceProto, from, 0);
    }

    public void handleWithdrawalMessage(Withdrawal w, Host from, short sourceProto, int channelID) {
        logger.info("Received withdrawal of " + w.getAmount() + " to " + w.getClientID().toString() + " from the Exchange (" + from + ")");

        opers.put(w.getRid(), OperationStatusReply.Status.PENDING);
        opers_body.put(w.getRid(), w);

        GenericClientReply ack = new GenericClientReply(w.getRid());

        sendMessage(clientChannel, ack, sourceProto, from, 0);
    }

    private void uponClientConnectionUp(ClientUpEvent event, int channel) {
        logger.debug(event);
    }

    private void uponClientConnectionDown(ClientDownEvent event, int channel) {
        logger.warn(event);
    }
}