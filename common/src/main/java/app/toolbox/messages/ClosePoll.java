package app.toolbox.messages;

import java.security.PublicKey;
import java.util.UUID;

import app.WriteOperation;

public class ClosePoll extends WriteOperation{

    public final static short MESSAGE_ID = 102;

    private final UUID rid;
    private final PublicKey clientID;
    private final UUID pollID;


    public ClosePoll(UUID rid, PublicKey pk, UUID pollID){
        super(ClosePoll.MESSAGE_ID);

        this.rid = rid;
        this.clientID = pk;
        this.pollID = pollID;
    }


    public UUID getRid() {
        return rid;
    }


    public PublicKey getClientID() {
        return clientID;
    }


    public UUID getPollID() {
        return pollID;
    }

    
    
}
