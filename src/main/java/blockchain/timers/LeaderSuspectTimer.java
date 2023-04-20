package blockchain.timers;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class LeaderSuspectTimer extends ProtoTimer {
	
	public final static short TIMER_ID = 202;

	public LeaderSuspectTimer() {
		super(LeaderSuspectTimer.TIMER_ID);
	}
	
	@Override
	public ProtoTimer clone() {
		return this;
	}

}
