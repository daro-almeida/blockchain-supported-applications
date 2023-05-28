package app.toolbox.messages;

import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class ClosePoll extends SignedProtoMessage{

    public final static short MESSAGE_ID = 102;

    public ClosePoll(){
        super(ClosePoll.MESSAGE_ID);
    }


    public static final SignedMessageSerializer<ClosePoll> serializer = new SignedMessageSerializer<>() {

        @Override
        public ClosePoll deserializeBody(ByteBuf arg0) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'deserializeBody'");
        }

        @Override
        public void serializeBody(ClosePoll arg0, ByteBuf arg1) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'serializeBody'");
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return ClosePoll.serializer;
    }

    
    
}
