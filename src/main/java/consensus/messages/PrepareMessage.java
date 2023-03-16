package consensus.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class PrepareMessage extends SignedProtoMessage {

	private final static short MESSAGE_ID = 102;	
	
	//TODO: Define here the elements of the message
	
	public PrepareMessage() {
		super(PrepareMessage.MESSAGE_ID);
		
	}

	public static final SignedMessageSerializer<PrepareMessage> serializer = new SignedMessageSerializer<PrepareMessage>() {

		@Override
		public void serializeBody(PrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			// TODO Auto-generated method stub, you should implement this method.
			
		}

		@Override
		public PrepareMessage deserializeBody(ByteBuf in) throws IOException {
			// TODO Auto-generated method stub, you should implement this method.
			return null;
		}
		
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrepareMessage.serializer;
	}

}
