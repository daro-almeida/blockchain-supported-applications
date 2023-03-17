package consensus.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class PrepareMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 102;
	

	public PrepareMessage() {
		super(PrepareMessage.MESSAGE_ID);
		
	}

	public static final SignedMessageSerializer<PrepareMessage> serializer = new SignedMessageSerializer<PrepareMessage>() {

		@Override
		public void serializeBody(PrepareMessage signedProtoMessage, ByteBuf out) throws IOException {

		}

		@Override
		public PrepareMessage deserializeBody(ByteBuf in) throws IOException {
			return null;
		}
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrepareMessage.serializer;
	}

}
