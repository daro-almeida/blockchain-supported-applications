package consensus.requests;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class SuspectLeader extends ProtoRequest {

	public final static short REQUEST_ID = 101;
	
	//Represents the client transaction ID that was not ordered by the leader
	//and justifies this suspicion (while will lead to a view change)
	//Note: Could potentially be a Set of pending requests identifiers)
	private final UUID pendingRequestID;
	
	public SuspectLeader(UUID pendingRequestID) {
		super(SuspectLeader.REQUEST_ID);
		this.pendingRequestID = pendingRequestID;
	}

	public UUID getPendingRequestID() {
		return pendingRequestID;
	}

}
