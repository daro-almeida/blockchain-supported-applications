package blockchain.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class NoOpTimer extends ProtoTimer {

    public final static short TIMER_ID = 204;

    public NoOpTimer() {
        super(NoOpTimer.TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
