package blockchain.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class RedirectClientRequestMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 201;
	
	public RedirectClientRequestMessage() {
		super(RedirectClientRequestMessage.MESSAGE_ID);
	}

	public static SignedMessageSerializer<RedirectClientRequestMessage> serializer = new SignedMessageSerializer<RedirectClientRequestMessage>() {
		
		@Override
		public void serializeBody(RedirectClientRequestMessage signedProtoMessage, ByteBuf out) throws IOException {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public RedirectClientRequestMessage deserializeBody(ByteBuf in) throws IOException {
			// TODO Auto-generated method stub
			return null;
		}
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return RedirectClientRequestMessage.serializer;
	}

}
