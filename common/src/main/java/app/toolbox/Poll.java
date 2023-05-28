package app.toolbox;

public abstract class Poll {
    
    public enum PollStatus {OPEN, CLOSED};
    // TODO in case it is closed, some form to allow the validation that a user is
    // allowed to participate in the poll.
    private final String description;
    private final int range;
    private final int nParticipants;
    private PollStatus status;

    public Poll(String description, int range, int nParticipants, PollStatus status) {
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

    public PollStatus getStatus() {
        return status;
    }

    public void setStatus(PollStatus status) {
        this.status = status;
    }
}
