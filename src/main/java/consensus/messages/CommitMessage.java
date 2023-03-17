package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;

public class CommitMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 103;

    public CommitMessage() {
        super(CommitMessage.MESSAGE_ID);
    }

    public static SignedMessageSerializer<CommitMessage> serializer = new SignedMessageSerializer<CommitMessage>() {

        @Override
        public void serializeBody(CommitMessage signedProtoMessage, ByteBuf out) throws IOException {

        }

        @Override
        public CommitMessage deserializeBody(ByteBuf in) throws IOException {
            return null;
        }
    };

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
