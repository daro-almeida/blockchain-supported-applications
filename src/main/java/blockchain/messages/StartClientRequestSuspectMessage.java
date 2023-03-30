package blockchain.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

//TODO implement this message
public class StartClientRequestSuspectMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 203;

	public StartClientRequestSuspectMessage() {
		super(MESSAGE_ID);
	}

	public static final SignedMessageSerializer<StartClientRequestSuspectMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(StartClientRequestSuspectMessage protoMessage, ByteBuf out) throws IOException {

		}

		@Override
		public StartClientRequestSuspectMessage deserializeBody(ByteBuf in) throws IOException {
			return new StartClientRequestSuspectMessage();
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
