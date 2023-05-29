package app.open_goods.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.util.UUID;

public class ExpiredOperation extends ProtoTimer {
	
	public final static short TIMER_ID = 1002;
	public final UUID req;
	public final SignedProtoMessage message;
	
	public ExpiredOperation(UUID req, SignedProtoMessage message) {
		super(TIMER_ID);
		this.req = req;
		this.message = message;
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

	
}
