package app.toolbox.messages;

import java.util.UUID;

public class NumericVote extends Vote{

    private final double value;

    public NumericVote(UUID rid, UUID pollID, double value){
        super(rid, pollID);
        this.value = value;
    }

    public double getValue() {
        return value;
    }
    
}
