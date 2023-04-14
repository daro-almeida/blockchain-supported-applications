package consensus.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class SuspectLeader extends ProtoRequest {

	public final static short REQUEST_ID = 102;

	private final int currentView;
	
	public SuspectLeader(int currentView) {
		super(SuspectLeader.REQUEST_ID);
		this.currentView = currentView;
	}

	public int getCurrentView() {
		return currentView;
	}

}
