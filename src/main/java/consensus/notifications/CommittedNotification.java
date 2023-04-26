package consensus.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class CommittedNotification extends ProtoNotification {

	public final static short NOTIFICATION_ID = 101;

	private final int seqN;
	private final byte[] block;
	private final byte[] signature;
	
	public CommittedNotification(int seqN, byte[] block, byte[] signature) {
		super(CommittedNotification.NOTIFICATION_ID);
		this.seqN = seqN;
		this.block = block;
		this.signature = signature;
	}

	public int getSeqN() {
		return seqN;
	}

	public byte[] getBlock() {
		return block;
	}

	public byte[] getSignature() {
		return signature;
	}
}
