package consensus.requests;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class SuspectLeader extends ProtoRequest {

	public final static short REQUEST_ID = 102;

	private final int currentViewNumber;
	
	public SuspectLeader(int currentViewNumber) {
		super(SuspectLeader.REQUEST_ID);
		this.currentViewNumber = currentViewNumber;
	}

	public int getCurrentViewNumber() {
		return currentViewNumber;
	}

}
