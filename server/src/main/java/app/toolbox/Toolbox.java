package app.toolbox;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.WriteOperation;
import app.open_goods.Destination;
import app.open_goods.messages.client.replies.GenericClientReply;
import app.open_goods.messages.client.replies.OperationStatusReply;
import app.open_goods.messages.client.replies.OperationStatusReply.Status;
import blockchain.BlockChainProtocol;
import blockchain.notifications.ExecutedOperation;
import consensus.PBFTProtocol;
import metrics.Metrics;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import utils.Crypto;
import utils.Utils;

public class Toolbox extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Toolbox.class);

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    public final static String PROTO_NAME = "Toolbox";
    public final static short PROTO_ID = 600;

    // incoming polls
    private final Map<UUID, Poll> polls = new HashMap<>();

    // TODO: change votes uuid to cote obj.
    private final Map<UUID, Set<UUID>> votes = new HashMap<>();

    // to send reply later
    private final Map<UUID, Destination> reqDestinations = new HashMap<>();

    private final Map<UUID, OperationStatusReply.Status> opStatus = new HashMap<>();

    private int clientChannel;

    public static void main(String[] args) throws Exception {
        Properties props = Babel.loadConfig(Arrays.copyOfRange(args, 0, args.length), "config.properties");
        logger.debug(props);
        if (props.containsKey("interface")) {
            String address = Utils.getAddress(props.getProperty("interface"));
            if (address == null)
                return;
            props.put(ADDRESS_KEY, address);
        }

        Metrics.initMetrics(props);

        Babel babel = Babel.getInstance();

        Toolbox toolbox = new Toolbox(props);
        BlockChainProtocol bc = new BlockChainProtocol(props);
        PBFTProtocol pbft = new PBFTProtocol(props);

        babel.registerProtocol(toolbox);
        babel.registerProtocol(bc);
        babel.registerProtocol(pbft);

        toolbox.init(props);
        bc.init(props);
        pbft.init(props);

        babel.start();

        logger.info("Running OpenGoodsMarket");
    }

    public Toolbox(Properties props) {
        super(PROTO_NAME, PROTO_ID);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, props.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, props.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);
        // register stuff here

        // registermsgserialzer from clients

        // handlers

        // Handle exec. operation
        registerChannelEventHandler(clientChannel, ClientUpEvent.EVENT_ID, this::uponClientConnectionUp);
        registerChannelEventHandler(clientChannel, ClientDownEvent.EVENT_ID, this::uponClientConnectionDown);

        subscribeNotification(ExecutedOperation.NOTIFICATION_ID, this::handleExecutedOperation);
    }

    /*
     * NOTIFICATIONS
     */

    private void handleExecutedOperation(ExecutedOperation notif, short sourceProto) {

        assert (opStatus.get(notif.getRequest().getRequestId()) == Status.PENDING ||
                opStatus.get(notif.getRequest().getRequestId()) == null);

        var operation = WriteOperation.fromBytes(notif.getRequest().getOperation());
        switch (operation.getId()) {
            //TODO: pollmessgs
            /* case IssueOffer.MESSAGE_ID -> handleExecutedIssueOffer((IssueOffer) operation);
            case IssueWant.MESSAGE_ID -> handleExecutedIssueWant((IssueWant) operation);
            case Cancel.MESSAGE_ID -> handleExecutedCancel((Cancel) operation);
            case Deposit.MESSAGE_ID -> handleExecutedDeposit((Deposit) operation);
            case Withdrawal.MESSAGE_ID -> handleExecutedWithdrawal((Withdrawal) operation); */
            default -> logger.error("Received unknown operation for open goods market");
        }

    }

    /*
     * CONNECTION EVENTS
     */

    private void uponClientConnectionUp(ClientUpEvent event, int channel) {
        logger.debug(event);
    }

    private void uponClientConnectionDown(ClientDownEvent event, int channel) {
        logger.warn(event);
    }

    private boolean repeatedOperation(UUID requestId, Destination dest) {
        if (opStatus.containsKey(requestId) && opStatus.get(requestId) != OperationStatusReply.Status.PENDING) {
            logger.warn("Repeated operation {}", requestId);
            opStatus.put(requestId, Status.FAILED);
            sendStatus(requestId, Status.FAILED, dest);
            return true;
        }
        return false;
    }

    private void sendStatus(UUID requestId, Status status, Destination dest) {
        if (dest == null)
            return;
        OperationStatusReply statusReply = new OperationStatusReply(requestId, status);
        sendMessage(clientChannel, statusReply, dest.sourceProto(), dest.host(), 0);
    }

    private void sendAck(UUID requestId, Destination dest) {
        assert dest != null;
        GenericClientReply ack = new GenericClientReply(requestId);
        sendMessage(clientChannel, ack, dest.sourceProto(), dest.host(), 0);
    }

    private void changeAndNotifyStatus(UUID requestId, Status status) {
        opStatus.put(requestId, status);
        var hostToReply = reqDestinations.get(requestId);
        if (hostToReply != null) {
            sendStatus(requestId, status, hostToReply);
            reqDestinations.remove(requestId);
        }
    }

    private boolean authenticatedOperation(WriteOperation op, UUID requestId, PublicKey key, Destination dest) {
        if (!Crypto.checkSignature(op, key)) {
            logger.warn("Invalid signature in operation from client {}", dest.host());
            opStatus.put(requestId, Status.REJECTED);
            sendStatus(requestId, Status.REJECTED, dest);
            return false;
        }
        return true;
    }
}
