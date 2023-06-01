package app.toolbox;

import app.BlockChainApplication;
import app.WriteOperation;
import app.open_goods.messages.client.replies.OperationStatusReply.Status;
import app.toolbox.messages.*;
import blockchain.BlockChainProtocol;
import blockchain.notifications.ExecutedOperation;
import consensus.PBFTProtocol;
import metrics.Metrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Utils;

import java.io.IOException;
import java.util.*;

public class Toolbox extends BlockChainApplication {

    private static final Logger logger = LogManager.getLogger(Toolbox.class);

    public final static String PROTO_NAME = "Toolbox";
    public final static short PROTO_ID = 600;

    // incoming polls
    private final Map<UUID, Poll> polls = new HashMap<>();

    // TODO: change votes uuid to cote obj.
    private final Map<UUID, Set<Vote>> votes = new HashMap<>();

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

        logger.info("Running Toolbox");
    }

    public Toolbox(Properties props) {
        super(PROTO_NAME, PROTO_ID, logger);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
        super.init(props);

        registerMessageSerializer(clientChannel, CreatePoll.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, Vote.MESSAGE_ID, WriteOperation.serializer);
        registerMessageSerializer(clientChannel, ClosePoll.MESSAGE_ID, WriteOperation.serializer);

        registerMessageHandler(clientChannel, CreatePoll.MESSAGE_ID, this::handleCreatePoll);
        registerMessageHandler(clientChannel, Vote.MESSAGE_ID, this::handleVote);
        registerMessageHandler(clientChannel, ClosePoll.MESSAGE_ID, this::handleClosePoll);
    }

    private void handleCreatePoll(CreatePoll msg, Host host, short protoId, int channelId) {
        logger.info("CreatePoll received id={} description={}", msg.getRid(), msg.getPoll().description);
        //TODO
    }

    private void handleVote(Vote<?> msg, Host host, short protoId, int channelId) {
        logger.info("Vote received id={} pollId={} value={}", msg.getRid(), msg.getPollID(), msg.getValue());
        //TODO
    }

    private void handleClosePoll(ClosePoll msg, Host host, short protoId, int channelId) {
        //TODO
    }


    protected void handleExecutedOperation(ExecutedOperation notif, short sourceProto) {
        assert (opStatus.get(notif.getRequest().getRequestId()) == Status.PENDING ||
                opStatus.get(notif.getRequest().getRequestId()) == null);

        var operation = WriteOperation.fromBytes(notif.getRequest().getOperation());
        switch (operation.getId()) {
            case ClosePoll.MESSAGE_ID -> handleExecutedClosePoll((ClosePoll) operation);
            case CreatePoll.MESSAGE_ID -> handleExecutedCreatePoll((CreatePoll) operation);
            case Vote.MESSAGE_ID -> handleExecutedVote((Vote<?>) operation);
            default -> logger.error("Received unknown operation for open goods market");
        }
    }

    private void handleExecutedClosePoll(ClosePoll closePoll) {
        //TODO
    }

    private void handleExecutedCreatePoll(CreatePoll createPoll) {
        //TODO
    }

    private void handleExecutedVote(Vote<?> vote) {
        //TODO
    }

    // TODO validate methods for each msg
}
