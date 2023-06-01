package app.toolbox.messages;

import app.WriteOperation;
import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public abstract class Vote<T> extends WriteOperation {

    public final static short MESSAGE_ID = 702;

    protected final UUID rid;
    private final PublicKey clientID;

    protected Poll.Type type;
    protected final UUID pollID;

    private final T value;

    protected Vote(Poll.Type type, UUID rid, PublicKey clientID, UUID pollID, T value){
        super(Vote.MESSAGE_ID);

        this.type = type;
        this.rid = rid;
        this.clientID = clientID;
        this.pollID = pollID;
        this.value = value;
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

    public Poll.Type getType() {
        return type;
    }

    public T getValue() {
        return value;
    }

    public static final ISerializer<Vote<?>> serializer = new ISerializer<>() {

        @Override
        public void serialize(Vote vote, ByteBuf byteBuf) throws IOException {
            byteBuf.writeShort(vote.type.ordinal());
            Utils.uuidSerializer.serialize(vote.rid, byteBuf);
            Utils.rsaPublicKeySerializer.serialize(vote.clientID, byteBuf);
            Utils.uuidSerializer.serialize(vote.pollID, byteBuf);

            switch (vote.type) {
                case NUMERIC -> NumericVote.valueSerializer.serialize(((NumericVote) vote).getValue(), byteBuf);
                case DISCRETE -> DiscreteVote.valueSerializer.serialize(((DiscreteVote) vote).getValue(), byteBuf);
            }
        }

        @Override
        public Vote<?> deserialize(ByteBuf byteBuf) throws IOException {
            Poll.Type type = Poll.Type.valueOf(byteBuf.readShort());
            UUID rid = Utils.uuidSerializer.deserialize(byteBuf);
            PublicKey clientID = Utils.rsaPublicKeySerializer.deserialize(byteBuf);
            UUID pollID = Utils.uuidSerializer.deserialize(byteBuf);

            return switch (type) {
                case NUMERIC -> new NumericVote(rid, clientID, pollID, NumericVote.valueSerializer.deserialize(byteBuf));
                case DISCRETE -> new DiscreteVote(rid, clientID, pollID, DiscreteVote.valueSerializer.deserialize(byteBuf));
            };
        }
    };
}
