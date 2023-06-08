package app.toolbox;

import app.BlockChainApplication;
import app.Destination;
import app.OperationStatusReply;
import app.OperationStatusReply.Status;
import app.WriteOperation;
import app.toolbox.messages.ClosePoll;
import app.toolbox.messages.CreatePoll;
import app.toolbox.messages.Vote;
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
import java.security.PublicKey;
import java.util.*;

public class Toolbox extends BlockChainApplication {

    private static final Logger logger = LogManager.getLogger(Toolbox.class);

    public final static String PROTO_NAME = "Toolbox";
    public final static short PROTO_ID = 600;

    // incoming currentPolls
    private final Map<UUID, Poll> currentPolls = new HashMap<>();
    private final Map<UUID, Poll> finishedPolls = new HashMap<>();
    private final Map<UUID, PublicKey> pollCreators = new HashMap<>();
    private final Map<UUID, Set<Vote<?>>> votes = new HashMap<>();

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

        registerMessageHandler(clientChannel, CreatePoll.MESSAGE_ID, this::handleCreatePollMessage);
        registerMessageHandler(clientChannel, Vote.MESSAGE_ID, this::handleVoteMessage);
        registerMessageHandler(clientChannel, ClosePoll.MESSAGE_ID, this::handleClosePollMessage);
    }

    private void handleCreatePollMessage(CreatePoll msg, Host host, short protoId, int channelId) {
        logger.debug("CreatePoll received id={} description={}", msg.getRid(), msg.getPoll().description);
        
        var dest = new Destination(host, protoId);
        if(!validateCreatePoll(msg, dest) || !authenticatedOperation(msg, msg.getRid(), msg.getClientID(), dest)){
            return;
        }

        submitOperation(msg.getRid(), msg.getBytes(), msg.getSignature(), msg.getClientID(), dest);
    }

    private void handleVoteMessage(Vote<?> msg, Host host, short protoId, int channelId) {
        logger.debug("Vote received id={} pollId={} value={}", msg.getRid(), msg.getPollID(), msg.getValue());
        
        var dest = new Destination(host, protoId);
        if(!validateVote(msg, dest) || !authenticatedOperation(msg, msg.getRid(), msg.getClientID(), dest)){
            return;
        }

        submitOperation(msg.getRid(), msg.getBytes(), msg.getSignature(), msg.getClientID(), dest);
    }

    private void handleClosePollMessage(ClosePoll msg, Host host, short protoId, int channelId) {

        var dest = new Destination(host, protoId);
        if(!validateClosePoll(msg, dest) || !authenticatedOperation(msg, msg.getRid(), msg.getClientID(), dest)){
            return;
        }

        submitOperation(msg.getRid(), msg.getBytes(), msg.getSignature(), msg.getClientID(), dest);
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

    private void handleExecutedCreatePoll(CreatePoll createPoll) {
        if(!validateCreatePoll(createPoll, reqDestinations.get(createPoll.getRid()))){
            reqDestinations.remove(createPoll.getRid());
            return;
        }

        logger.info("Executed CreatePoll: pollId={} description={}", createPoll.getRid(), createPoll.getPoll().getDescription());

        currentPolls.put(createPoll.getRid(), createPoll.getPoll());
        pollCreators.put(createPoll.getRid(), createPoll.getClientID());
        votes.put(createPoll.getRid(), new HashSet<>());

        changeAndNotifyStatus(createPoll.getRid(), Status.EXECUTED);
    }

    private void handleExecutedClosePoll(ClosePoll closePoll) {
        if(!validateClosePoll(closePoll, reqDestinations.get(closePoll.getRid()))){
            reqDestinations.remove(closePoll.getRid());
            return;
        }

        logger.info("Executed ClosePoll: pollId={}", closePoll.getRid());

        UUID pollID = closePoll.getPollID();
        Poll poll = currentPolls.get(pollID);
        finishedPolls.put(pollID, poll);
        currentPolls.remove(pollID);

        changeAndNotifyStatus(closePoll.getRid(), Status.EXECUTED);
    }

    private void handleExecutedVote(Vote<?> vote) {
        if(!validateVote(vote, reqDestinations.get(vote.getRid()))){
            reqDestinations.remove(vote.getRid());
            return;
        }

        logger.info("Executed Vote: pollId={} value={}", vote.getRid(), vote.getValue());

        UUID pollID = vote.getPollID();
        Poll poll = currentPolls.get(pollID);
        votes.get(pollID).add(vote);

        if(votes.get(pollID).size() == poll.getMaxParticipants()){
            finishedPolls.put(pollID, poll);
            currentPolls.remove(pollID);
            logger.info("Poll {} finished", pollID);
        }

        changeAndNotifyStatus(vote.getRid(), Status.EXECUTED);
    }

    private boolean validateCreatePoll(CreatePoll msg, Destination dest) {
        if(repeatedOperation(msg.getRid(), dest)){
            return false;
        }

        boolean failed = false;
        if( msg.getPoll().maxParticipants <= 0){
            logger.warn("CreatePoll: Invalid number of participants in {} from client {}", msg.getRid(), dest.host());
            failed = true;
        } else if(!msg.getPoll().validCreation()){
            logger.warn("CreatePoll: Invalid poll {} from client {}", msg.getRid(), dest.host());
            failed = true;
        }

        if(failed){
            opStatus.put(msg.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(msg.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }

    private boolean validateVote(Vote<?> msg, Destination dest) {
        if(repeatedOperation(msg.getRid(), dest)){
            return false;
        }

        boolean failed = false;
        UUID pollID = msg.getPollID();
        Poll poll = currentPolls.get(pollID);

        if( poll == null){
            logger.warn("Vote: Poll {} doesn't exist or was closed", msg.getPollID());
            failed = true;
        } else if( !poll.validVote(msg) ){
            logger.warn("Vote: Invalid vote {} from client {}", msg.getRid(), dest.host());
            failed = true;
        } else if( !poll.canVote(msg.getClientID())){
            logger.warn("Vote: Client {} can't vote on poll {}", dest.host(), msg.getPollID());
            failed = true;
        }

        if(failed){
            opStatus.put(msg.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(msg.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }
        return true;
    }

    private boolean validateClosePoll(ClosePoll msg, Destination dest) {
        if(repeatedOperation(msg.getRid(), dest)){
            return false;
        }

        boolean failed = false;
        var pollID = msg.getPollID();
        Poll poll = currentPolls.get(pollID);

        if( poll == null){
            logger.warn("ClosePoll: Poll {} doesn't exist or was closed", msg.getPollID());
            failed = true;
        } else if ( !pollCreators.get(pollID).equals(msg.getClientID()) ){
            logger.warn("ClosePoll: Client {} is not authorized to close poll {}", dest.host(), msg.getRid());
            failed = true;
        }

        if(failed){
            opStatus.put(msg.getRid(), OperationStatusReply.Status.FAILED);
            sendStatus(msg.getRid(), OperationStatusReply.Status.FAILED, dest);
            return false;
        }

        return true;
    }
}
