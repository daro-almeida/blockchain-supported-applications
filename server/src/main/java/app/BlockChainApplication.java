package app;

import app.open_goods.messages.client.replies.GenericClientReply;
import app.open_goods.messages.client.requests.CheckOperationStatus;
import blockchain.BlockChainProtocol;
import blockchain.notifications.ExecutedOperation;
import blockchain.requests.ClientRequest;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.simpleclientserver.SimpleServerChannel;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientDownEvent;
import pt.unl.fct.di.novasys.channel.simpleclientserver.events.ClientUpEvent;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public abstract class BlockChainApplication extends GenericProtocol {

    public static final String ADDRESS_KEY = "address";
    public static final String SERVER_PORT_KEY = "client_server_port";

    private final Logger logger;

    protected int clientChannel;

    protected final Map<UUID, OperationStatusReply.Status> opStatus = new HashMap<>();
    // to send reply later if needed
    protected final Map<UUID, Destination> reqDestinations = new HashMap<>();


    public BlockChainApplication(String protoName, short protoId, Logger logger) {
        super(protoName, protoId);
        this.logger = logger;
    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
        Properties serverProps = new Properties();
        serverProps.put(SimpleServerChannel.ADDRESS_KEY, properties.getProperty(ADDRESS_KEY));
        serverProps.setProperty(SimpleServerChannel.PORT_KEY, properties.getProperty(SERVER_PORT_KEY));

        clientChannel = createChannel(SimpleServerChannel.NAME, serverProps);

        registerMessageSerializer(clientChannel, CheckOperationStatus.MESSAGE_ID, CheckOperationStatus.serializer);
        registerMessageSerializer(clientChannel, OperationStatusReply.MESSAGE_ID, OperationStatusReply.serializer);
        registerMessageSerializer(clientChannel, GenericClientReply.MESSAGE_ID, GenericClientReply.serializer);

        registerMessageHandler(clientChannel, CheckOperationStatus.MESSAGE_ID, this::handleCheckOperationStatusMessage);

        registerChannelEventHandler(clientChannel, ClientUpEvent.EVENT_ID, this::uponClientConnectionUp);
        registerChannelEventHandler(clientChannel, ClientDownEvent.EVENT_ID, this::uponClientConnectionDown);

        subscribeNotification(ExecutedOperation.NOTIFICATION_ID, this::handleExecutedOperation);
    }

    protected abstract void handleExecutedOperation(ExecutedOperation executedOperation, short protoId);

    protected void handleCheckOperationStatusMessage(CheckOperationStatus cos, Host from, short sourceProto, int channelID) {
        logger.debug("Received CheckOperationStatus ({} from client {})", cos.getrID(), from);

        OperationStatusReply osr;

        OperationStatusReply.Status s = opStatus.get(cos.getrID());

        if (s != null) {
            osr = switch (s) {
                case CANCELLED -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.CANCELLED);
                case EXECUTED -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.EXECUTED);
                case FAILED -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.FAILED);
                case PENDING -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.PENDING);
                case REJECTED -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.REJECTED);
                default -> new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.UNKNOWN);
            };
        } else {
            osr = new OperationStatusReply(cos.getrID(), OperationStatusReply.Status.UNKNOWN);
        }

        sendMessage(clientChannel, osr, sourceProto, from, 0);
    }

    private void uponClientConnectionUp(ClientUpEvent event, int channel) {
        logger.debug(event);
    }

    private void uponClientConnectionDown(ClientDownEvent event, int channel) {
        logger.warn(event);
    }

    protected boolean authenticatedOperation(WriteOperation op, UUID requestId, PublicKey key, Destination dest) {
        if (!Crypto.checkSignature(op, key)) {
            logger.warn("Invalid signature in operation from client {}", dest.host());
            opStatus.put(requestId, OperationStatusReply.Status.REJECTED);
            sendStatus(requestId, OperationStatusReply.Status.REJECTED, dest);
            return false;
        }
        return true;
    }

    protected boolean repeatedOperation(UUID requestId, Destination dest) {
        if (opStatus.containsKey(requestId) && opStatus.get(requestId) != OperationStatusReply.Status.PENDING) {
            logger.warn("Repeated operation {}", requestId);
            opStatus.put(requestId, OperationStatusReply.Status.FAILED);
            sendStatus(requestId, OperationStatusReply.Status.FAILED, dest);
            return true;
        }
        return false;
    }

    protected void submitOperation(UUID requestId, byte[] opBytes, byte[] signature, PublicKey clientId, Destination dest) {
        var req = new ClientRequest(requestId, opBytes, signature, clientId);
        sendRequest(req, BlockChainProtocol.PROTO_ID);
        opStatus.put(requestId, OperationStatusReply.Status.PENDING);
        reqDestinations.put(requestId, dest);

        sendAck(requestId, reqDestinations.get(requestId));
    }

    protected void sendAck(UUID requestId, Destination dest) {
        assert dest != null;
        GenericClientReply ack = new GenericClientReply(requestId);
        sendMessage(clientChannel, ack, dest.sourceProto(), dest.host(), 0);
    }

    protected void changeAndNotifyStatus(UUID requestId, OperationStatusReply.Status status) {
        opStatus.put(requestId, status);
        var hostToReply = reqDestinations.get(requestId);
        if (hostToReply != null) {
            sendStatus(requestId, status, hostToReply);
            reqDestinations.remove(requestId);
        }
    }
    protected void sendStatus(UUID requestId, OperationStatusReply.Status status, Destination dest) {
        if (dest == null)
            return;
        OperationStatusReply statusReply = new OperationStatusReply(requestId, status);
        sendMessage(clientChannel, statusReply, dest.sourceProto(), dest.host(), 0);
    }
}
