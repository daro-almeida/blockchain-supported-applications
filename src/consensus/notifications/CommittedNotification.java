package consensus.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class CommittedNotification extends ProtoNotification {

	public final static short NOTIFICATION_ID = 101;
	
	private final byte[] block;
	private final byte[] signature;
	
	public CommittedNotification(byte[] block, byte[] signature) {
		super(CommittedNotification.NOTIFICATION_ID);
		this.block = block;
		this.signature = signature;
	}

	public byte[] getBlock() {
		return block;
	}

	public byte[] getSignature() {
		return signature;
	}
	
	

}
