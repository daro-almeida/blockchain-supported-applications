package app.toolbox.messages;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class Vote extends SignedProtoMessage {

    public final static short MESSAGE_ID = 102;

    public Vote(){
        super(Vote.MESSAGE_ID);
    }


    public static final SignedMessageSerializer<Vote> serializer = new SignedMessageSerializer<>() {

        @Override
        public Vote deserializeBody(ByteBuf arg0) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'deserializeBody'");
        }

        @Override
        public void serializeBody(Vote arg0, ByteBuf arg1) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'serializeBody'");
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return Vote.serializer;
    }

}
