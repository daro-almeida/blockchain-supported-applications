package app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

import java.util.UUID;

public class NextCheck extends ProtoTimer {
	
	public final static short TIMER_ID = 1001;
	public final UUID req;
	
	public NextCheck(UUID req) {
		super(TIMER_ID);
		this.req = req;
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

	
}
