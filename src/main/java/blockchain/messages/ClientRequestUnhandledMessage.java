package blockchain.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

//TODO implement this message
public class ClientRequestUnhandledMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 202;

	public ClientRequestUnhandledMessage() {
		super(MESSAGE_ID);
	}

	public static final SignedMessageSerializer<ClientRequestUnhandledMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(ClientRequestUnhandledMessage protoMessage, ByteBuf out) throws IOException {

		}

		@Override
		public ClientRequestUnhandledMessage deserializeBody(ByteBuf in) throws IOException {
			return new ClientRequestUnhandledMessage();
		}
	};


	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
