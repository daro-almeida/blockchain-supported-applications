package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class PrepareMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 102;

	private final int viewNumber, seq;
	private final byte[] digest;
	private final int nodeId;

	public PrepareMessage(PrePrepareMessage msg, int nodeId) {
		this(msg.getViewNumber(), msg.getSeq(), msg.getDigest(), nodeId);

	}

	public PrepareMessage(int viewNumber, int seq, byte[] digest, int nodeId) {
		super(PrepareMessage.MESSAGE_ID);
		this.viewNumber = viewNumber;
		this.seq = seq;
		this.digest = digest;
		this.nodeId = nodeId;
	}

	public int getViewNumber() {
		return viewNumber;
	}

	public int getSeq() {
		return seq;
	}

	public byte[] getDigest() {
		return digest;
	}

	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<PrepareMessage> serializer = new SignedMessageSerializer<PrepareMessage>() {

		@Override
		public void serializeBody(PrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			out.writeInt(signedProtoMessage.viewNumber);
            out.writeInt(signedProtoMessage.seq);
            Utils.byteArraySerializer.serialize(signedProtoMessage.digest, out);
            out.writeInt(signedProtoMessage.nodeId);
			
		}

		@Override
		public PrepareMessage deserializeBody(ByteBuf in) throws IOException {
			int viewN = in.readInt();
			int seq = in.readInt();
			byte[] digest = Utils.byteArraySerializer.deserialize(in);
			int nodeId = in.readInt();
			return new PrepareMessage(viewN, seq, digest, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrepareMessage.serializer;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PrepareMessage that = (PrepareMessage) o;
		return getViewNumber() == that.getViewNumber() && getSeq() == that.getSeq() && getNodeId() == that.getNodeId() && Arrays.equals(getDigest(), that.getDigest());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(getViewNumber(), getSeq(), getNodeId());
		result = 31 * result + Arrays.hashCode(getDigest());
		return result;
	}
}
