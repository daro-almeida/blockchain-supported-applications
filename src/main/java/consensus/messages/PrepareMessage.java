package consensus.messages;

import java.io.IOException;
import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

public class PrepareMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 102;

	private final int viewN, seq;
	private final byte[] digest;
	private final String cryptoName;

	public String getCryptoName() {
		return cryptoName;
	}

	public int getViewN() {
		return viewN;
	}

	public int getSeq() {
		return seq;
	}

	public byte[] getDigest() {
		return digest;
	}

	public PrepareMessage(PrePrepareMessage msg, String cryptoName) {
		this(msg.getViewN(), msg.getSeq(), msg.getDigest(), cryptoName);

	}

	public PrepareMessage(int viewN, int seq, byte[] digest, String cryptoName) {
		super(PrepareMessage.MESSAGE_ID);
		this.viewN = viewN;
		this.seq = seq;
		this.digest = digest;
		this.cryptoName= cryptoName;
	}

	public static final SignedMessageSerializer<PrepareMessage> serializer = new SignedMessageSerializer<PrepareMessage>() {

		@Override
		public void serializeBody(PrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			out.writeInt(signedProtoMessage.viewN);
            out.writeInt(signedProtoMessage.seq);
            out.writeInt(signedProtoMessage.digest.length);
            out.writeBytes(signedProtoMessage.digest);
            Utils.stringSerializer.serialize(signedProtoMessage.cryptoName, out);
			
		}

		@Override
		public PrepareMessage deserializeBody(ByteBuf in) throws IOException {
			int viewN = in.readInt();
            int seq = in.readInt();
            int digestLength = in.readInt();
            byte[] digest = new byte[digestLength];
            in.readBytes(digest);
            String cryptoName = Utils.stringSerializer.deserialize(in);
            return new PrepareMessage(viewN, seq, digest, cryptoName);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrepareMessage.serializer;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PrepareMessage other = (PrepareMessage) obj;
		if (viewN != other.viewN)
			return false;
		if (seq != other.seq)
			return false;
		if (!Arrays.equals(digest, other.digest))
			return false;
		if (cryptoName == null) {
			if (other.cryptoName != null)
				return false;
		} else if (!cryptoName.equals(other.cryptoName))
			return false;
		return true;
	}

}
