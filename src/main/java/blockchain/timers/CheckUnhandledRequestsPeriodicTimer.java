package blockchain.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class CheckUnhandledRequestsPeriodicTimer extends ProtoTimer {
	
	public final static short TIMER_ID = 201;

	public CheckUnhandledRequestsPeriodicTimer() {
		super(CheckUnhandledRequestsPeriodicTimer.TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
