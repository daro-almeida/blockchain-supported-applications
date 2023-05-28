package app.toolbox;

import java.security.PublicKey;
import java.util.Set;

public class DiscretePoll extends Poll{

    private final Set<String> values;


    public DiscretePoll(String description, int maxParticipants, Set<PublicKey> authorized, Set<String> values) {
        super(description, maxParticipants, authorized);
        this.values = values;
    }

    public DiscretePoll(String description, int maxParticipants, Set<String> values) {
        super(description, maxParticipants);
        this.values = values;
    }

    public Set<String> getValues() {
        return values;
    }
    
}
