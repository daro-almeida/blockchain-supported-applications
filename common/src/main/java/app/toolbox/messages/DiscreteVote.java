package app.toolbox.messages;

import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class DiscreteVote extends Vote {

    private final String value;

    public DiscreteVote(UUID rid, PublicKey clientID, UUID pollID, String value){
        super(Poll.Type.DISCRETE, rid, clientID, pollID);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final ISerializer<DiscreteVote> serializer = new ISerializer<>() {

        @Override
        public void serialize(DiscreteVote vote, ByteBuf byteBuf) throws IOException {
            Utils.stringSerializer.serialize(vote.value, byteBuf);
        }

        @Override
        public DiscreteVote deserialize(ByteBuf byteBuf) throws IOException {
            UUID rid = Utils.uuidSerializer.deserialize(byteBuf);
            PublicKey clientID = Utils.rsaPublicKeySerializer.deserialize(byteBuf);
            UUID pollID = Utils.uuidSerializer.deserialize(byteBuf);
            String value = Utils.stringSerializer.deserialize(byteBuf);
            return new DiscreteVote(rid, clientID, pollID, value);
        }
    };
}
