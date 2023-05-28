package app.toolbox.messages;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class CreatePoll extends SignedProtoMessage {
    
    public final static short MESSAGE_ID = 101;

    public enum PollStatus {OPEN, CLOSED};
    // TODO in case it is closed, some form to allow the validation that a user is
    // allowed to participate in the poll.
    private final UUID rid;
	private final PublicKey clientID;
    private Poll poll;

    public CreatePoll(UUID rid, PublicKey clientID, String description, int range, int nParticipants, PollStatus status) {
        super(CreatePoll.MESSAGE_ID);

        this.rid = rid;
        this.clientID = clientID;
        this.poll = new Poll();
        
    }

    

	public static final SignedMessageSerializer<CreatePoll> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(CreatePoll signedProtoMessage, ByteBuf out) throws IOException {
            
            
        }


        @Override
        public CreatePoll deserializeBody(ByteBuf arg0) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'deserializeBody'");
        }


        

        
        
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CreatePoll.serializer;
    }


}
