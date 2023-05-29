package app.toolbox.messages;

import app.WriteOperation;
import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public abstract class Vote extends WriteOperation {

    public final static short MESSAGE_ID = 702;

    protected final UUID rid;
    private final PublicKey clientID;

    protected Poll.Type type;
    protected final UUID pollID;

    protected Vote(Poll.Type type, UUID rid, PublicKey clientID, UUID pollID){
        super(Vote.MESSAGE_ID);

        this.type = type;
        this.rid = rid;
        this.clientID = clientID;

        this.pollID = pollID;
    }

    public final UUID getRid() {
        return rid;
    }

    public final PublicKey getClientID() {
        return clientID;
    }

    public final UUID getPollID() {
        return pollID;
    }

    public static final ISerializer<Vote> serializer = new ISerializer<>() {

        @Override
        public void serialize(Vote vote, ByteBuf byteBuf) throws IOException {
            byteBuf.writeShort(vote.type.ordinal());

            Utils.uuidSerializer.serialize(vote.rid, byteBuf);
            Utils.rsaPublicKeySerializer.serialize(vote.clientID, byteBuf);
            Utils.uuidSerializer.serialize(vote.pollID, byteBuf);
            switch (vote.type) {
                case NUMERIC -> NumericVote.serializer.serialize((NumericVote) vote, byteBuf);
                case DISCRETE -> DiscreteVote.serializer.serialize((DiscreteVote) vote, byteBuf);
                default -> throw new RuntimeException("Unknown poll type: " + vote.type);
            }
        }

        @Override
        public Vote deserialize(ByteBuf byteBuf) throws IOException {
            Poll.Type type = Poll.Type.valueOf(byteBuf.readShort());

            return switch (type) {
                case NUMERIC -> NumericVote.serializer.deserialize(byteBuf);
                case DISCRETE -> DiscreteVote.serializer.deserialize(byteBuf);
            };
        }
    };
}
