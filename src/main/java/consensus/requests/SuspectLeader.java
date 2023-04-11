package consensus.requests;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class SuspectLeader extends ProtoRequest {

	public final static short REQUEST_ID = 102;

	private final int newViewNumber;
	
	public SuspectLeader(int newViewNumber) {
		super(SuspectLeader.REQUEST_ID);
		this.newViewNumber = newViewNumber;
	}

	public int getNewViewNumber() {
		return newViewNumber;
	}

}
