package blockchain.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class LeaderIdleTimer extends ProtoTimer {

    public final static short TIMER_ID = 203;

    public LeaderIdleTimer() {
        super(LeaderIdleTimer.TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
