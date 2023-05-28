package app.toolbox.messages;

import java.util.UUID;

public class DiscreteVote extends Vote {
    public final static short MESSAGE_ID = 103;

    private final String value;

    public DiscreteVote(UUID rid, UUID pollID, String value){
        super(rid, pollID);
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    
}
