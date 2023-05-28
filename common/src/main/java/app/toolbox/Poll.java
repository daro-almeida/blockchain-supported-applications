package app.toolbox;

import java.security.PublicKey;
import java.util.Set;

public abstract class Poll {
    
    public enum Authorization {OPEN, CLOSED};
    protected final String description;
    protected final int maxParticipants;
    protected final Authorization authorization;
    protected final Set<PublicKey> authorized;

    protected Poll(String description, int maxParticipants, Set<PublicKey> authorized) {
        this.description = description;
        this.maxParticipants = maxParticipants;
        this.authorized = authorized;
        this.authorization = Authorization.CLOSED;
    }

    protected Poll(String description, int maxParticipants) {
        this.description = description;
        this.maxParticipants = maxParticipants;
        this.authorized = null;
        this.authorization = Authorization.OPEN;
    }

    public String getDescription() {
        return description;
    }

    public int getmaxParticipants() {
        return maxParticipants;
    }

    public Authorization getauthorization() {
        return authorization;
    }

    public Set<PublicKey> getAuthorized() {
        return authorized;
    }

}
