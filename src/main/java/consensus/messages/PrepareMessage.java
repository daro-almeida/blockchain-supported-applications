package consensus.messages;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

public class PrepareMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 102;

	private final int viewNumber, seq;
	private final byte[] digest;
	private final String cryptoName;

	public String getCryptoName() {
		return cryptoName;
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

	public PrepareMessage(PrePrepareMessage msg, String cryptoName) {
		this(msg.getViewNumber(), msg.getSeq(), msg.getDigest(), cryptoName);

	}

	public PrepareMessage(int viewNumber, int seq, byte[] digest, String cryptoName) {
		super(PrepareMessage.MESSAGE_ID);
		this.viewNumber = viewNumber;
		this.seq = seq;
		this.digest = digest;
		this.cryptoName= cryptoName;
	}

	public static final SignedMessageSerializer<PrepareMessage> serializer = new SignedMessageSerializer<PrepareMessage>() {

		@Override
		public void serializeBody(PrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			out.writeInt(signedProtoMessage.viewNumber);
            out.writeInt(signedProtoMessage.seq);
            Utils.byteArraySerializer.serialize(signedProtoMessage.digest, out);
            Utils.stringSerializer.serialize(signedProtoMessage.cryptoName, out);
			
		}

		@Override
		public PrepareMessage deserializeBody(ByteBuf in) throws IOException {
			int viewN = in.readInt();
			int seq = in.readInt();
			byte[] digest = Utils.byteArraySerializer.deserialize(in);
			String cryptoName = Utils.stringSerializer.deserialize(in);
			return new PrepareMessage(viewN, seq, digest, cryptoName);
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
		return getViewNumber() == that.getViewNumber() && getSeq() == that.getSeq() && Arrays.equals(getDigest(), that.getDigest()) && getCryptoName().equals(that.getCryptoName());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(getViewNumber(), getSeq(), getCryptoName());
		result = 31 * result + Arrays.hashCode(getDigest());
		return result;
	}
}
