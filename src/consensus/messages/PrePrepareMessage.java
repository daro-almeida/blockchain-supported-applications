package consensus.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class PrePrepareMessage extends SignedProtoMessage {

	private final static short MESSAGE_ID = 101;
	
	//TODO: Define here the elements of the message
	
	public PrePrepareMessage() {
		super(PrePrepareMessage.MESSAGE_ID);
	}

	public static SignedMessageSerializer<PrePrepareMessage> serializer = new SignedMessageSerializer<PrePrepareMessage>() {

		@Override
		public void serializeBody(PrePrepareMessage signedProtoMessage, ByteBuf out) throws IOException {
			// TODO Auto-generated method stub, you should implement this method.
			
		}

		@Override
		public PrePrepareMessage deserializeBody(ByteBuf in) throws IOException {
			// TODO Auto-generated method stub, you should implement this method.
			return null;
		}
		
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return PrePrepareMessage.serializer;
	}

}
