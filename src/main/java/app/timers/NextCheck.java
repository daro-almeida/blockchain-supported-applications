package app.timers;

import java.util.UUID;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

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
