package blockchain.messages;

import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class StartClientRequestSuspectMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 203;

	public StartClientRequestSuspectMessage() {
		super(MESSAGE_ID);
	}

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return null;
	}
}
