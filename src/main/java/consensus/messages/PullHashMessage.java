package consensus.messages;

import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

//TODO
public class PullHashMessage extends SignedProtoMessage {

    public static final short MESSAGE_ID = 108;

    public PullHashMessage() {
        super(MESSAGE_ID);
    }

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return null;
    }
}
