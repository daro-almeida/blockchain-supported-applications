package blockchain.messages;

import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class ClientRequestUnhandledMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 202;

	public ClientRequestUnhandledMessage() {
		super(MESSAGE_ID);
	}


	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return null;
	}
}
