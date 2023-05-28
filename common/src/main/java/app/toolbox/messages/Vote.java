package app.toolbox.messages;

import java.util.UUID;
import app.WriteOperation;

public class Vote extends WriteOperation {

    public final static short MESSAGE_ID = 103;

    protected final UUID rid;
    protected final UUID pollID;

    public Vote(UUID rid, UUID pollID){
        super(Vote.MESSAGE_ID);

        this.rid = rid;
        this.pollID = pollID;
    }

    public UUID getRid() {
        return rid;
    }

    public UUID getPollID() {
        return pollID;
    }



}
