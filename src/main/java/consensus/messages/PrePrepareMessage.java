package consensus.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

public class PrePrepareMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 101;

	private int viewN, seq;
	private byte[] digest;
	private Host sender;

	public PrePrepareMessage() {
		super(PrePrepareMessage.MESSAGE_ID);
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

	public Host getSender() {
		return sender;
	}

	public static final SignedMessageSerializer<PrePrepareMessage> serializer = new SignedMessageSerializer<PrePrepareMessage>() {

		@Override
		public void serializeBody(PrePrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			out.writeInt(signedProtoMessage.viewN);
			out.writeInt(signedProtoMessage.seq);
			out.writeInt(signedProtoMessage.digest.length);
			out.writeBytes(signedProtoMessage.digest); 
			Host.serializer.serialize(signedProtoMessage.sender, out);
		}

		@Override
		public PrePrepareMessage deserializeBody(ByteBuf in) throws IOException {
			PrePrepareMessage msg = new PrePrepareMessage();
            msg.viewN = in.readInt();
            msg.seq = in.readInt();
            msg.digest = new byte[in.readInt()];
            in.readBytes(msg.digest);
            msg.sender = Host.serializer.deserialize(in);
            return msg;
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrePrepareMessage.serializer;
	}

}
