package app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class NextOperation extends ProtoTimer {

	public final static short TIMER_ID = 1000;
	
	public NextOperation() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
