package blockchain.timers;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class LeaderSuspectTimer extends ProtoTimer {
	
	public final static short TIMER_ID = 202;

	private final UUID requestID;
	
	public LeaderSuspectTimer(UUID requestID) {
		super(LeaderSuspectTimer.TIMER_ID);
		this.requestID = requestID;
	}
	
	@Override
	public ProtoTimer clone() {
		return this;
	}

	public UUID getRequestID() {
		return requestID;
	}

}
