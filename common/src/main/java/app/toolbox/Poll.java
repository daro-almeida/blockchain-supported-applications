package app.toolbox;

public abstract class Poll {
    
    public enum Authorization {OPEN, CLOSED};
    // TODO in case it is closed, some form to allow the validation that a user is
    // allowed to participate in the poll.
    private final String description;
    private final int range;
    private final int nParticipants;
    private Authorization status;

    public Poll(String description, int range, int nParticipants, Authorization status) {
        this.description = description;
        this.range = range;
        this.nParticipants = nParticipants;
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public int getRange() {
        return range;
    }

    public int getnParticipants() {
        return nParticipants;
    }

    public Authorization getStatus() {
        return status;
    }

    public void setStatus(Authorization status) {
        this.status = status;
    }
}
