package app.toolbox;

import java.security.PublicKey;
import java.util.Set;

public class NumericPoll extends Poll{

    private final double min;
    private final double max;

    public NumericPoll(String description, int range, Set<PublicKey> authorized, double min, double max) {
        super(description, range, authorized);
        this.min = min;
        this.max = max;
    }

    public NumericPoll(String description, int range, double min, double max) {
        super(description, range);
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
    
}
