package blockchain.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class ForceBlockTimer extends ProtoTimer {
    public final static short TIMER_ID = 205;

    public ForceBlockTimer() {
        super(ForceBlockTimer.TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
