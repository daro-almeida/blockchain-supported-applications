package app.toolbox.messages;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

import app.WriteOperation;
import app.toolbox.Poll;


public class CreatePoll extends WriteOperation {
    
    public final static short MESSAGE_ID = 101;

    public enum PollStatus {OPEN, CLOSED};
    
    private final UUID rid;
	private final PublicKey clientID;
    private final Poll poll;

    public CreatePoll(UUID rid, PublicKey clientID, Poll poll) {
        super(CreatePoll.MESSAGE_ID);

        this.rid = rid;
        this.clientID = clientID;
        this.poll = poll;
        
    }

    public UUID getRid() {
        return rid;
    }

    public PublicKey getClientID() {
        return clientID;
    }

    public Poll getPoll() {
        return poll;
    }


}
