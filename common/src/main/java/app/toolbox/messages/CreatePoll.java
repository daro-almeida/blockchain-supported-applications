package app.toolbox.messages;

import app.WriteOperation;
import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;


public class CreatePoll extends WriteOperation {
    
    public final static short MESSAGE_ID = 701;

    private final UUID rid;
	private final PublicKey clientID;

    private final Poll poll;

    public CreatePoll(UUID rid, PublicKey clientID, Poll poll) {
        super(CreatePoll.MESSAGE_ID);

        this.rid = rid;
        this.clientID = clientID;
        this.poll = poll;
        
    }

    public UUID getRid() {
        return rid;
    }

    public PublicKey getClientID() {
        return clientID;
    }

    public Poll getPoll() {
        return poll;
    }

    public static final ISerializer<CreatePoll> serializer = new ISerializer<>() {

        @Override
        public void serialize(CreatePoll createPoll, ByteBuf byteBuf) throws IOException {
            Utils.uuidSerializer.serialize(createPoll.rid, byteBuf);
            Utils.rsaPublicKeySerializer.serialize(createPoll.clientID, byteBuf);
            Poll.serializer.serialize(createPoll.poll, byteBuf);
        }

        @Override
        public CreatePoll deserialize(ByteBuf byteBuf) throws IOException {
            UUID rid = Utils.uuidSerializer.deserialize(byteBuf);
            PublicKey clientID = Utils.rsaPublicKeySerializer.deserialize(byteBuf);
            Poll poll = Poll.serializer.deserialize(byteBuf);
            return new CreatePoll(rid, clientID, poll);
        }
    };
}
