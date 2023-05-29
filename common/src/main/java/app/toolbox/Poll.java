package app.toolbox;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Set;

public abstract class Poll {

    public enum Authorization {OPEN, CLOSED;
        private static final Authorization[] values = Authorization.values();
        public static Authorization valueOf(int ordinal) {
            return values[ordinal];
        }
    };

    public enum Type {
        DISCRETE, NUMERIC;
        private static final Type[] values = Type.values();
        public static Type valueOf(int ordinal) {
            return values[ordinal];
        }
    };

    protected final String description;
    protected final int maxParticipants;
    protected final Authorization authorization;
    protected final Set<PublicKey> authorized;
    protected final Type type;

    protected Poll(Type type, String description, int maxParticipants, Set<PublicKey> authorized) {
        this.type = type;
        this.description = description;
        this.maxParticipants = maxParticipants;
        this.authorized = authorized;
        this.authorization = Authorization.CLOSED;
    }

    protected Poll(Type type, String description, int maxParticipants) {
        this.type = type;
        this.description = description;
        this.maxParticipants = maxParticipants;
        this.authorized = null;
        this.authorization = Authorization.OPEN;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public Authorization getauthorization() {
        return authorization;
    }

    public Set<PublicKey> getAuthorized() {
        return authorized;
    }

    public Type getType() {
        return type;
    }

    public abstract boolean validVote(Object vote);

    public static final ISerializer<Poll> serializer = new ISerializer<>() {

        @Override
        public void serialize(Poll poll, ByteBuf byteBuf) throws IOException {
            byteBuf.writeShort(poll.type.ordinal());
            byteBuf.writeShort(poll.authorization.ordinal());
            Utils.stringSerializer.serialize(poll.description, byteBuf);
            byteBuf.writeInt(poll.maxParticipants);
            if (poll.authorization == Authorization.CLOSED) {
                assert poll.authorized != null;
                byteBuf.writeInt(poll.authorized.size());
                for (PublicKey key : poll.authorized)
                    Utils.rsaPublicKeySerializer.serialize(key, byteBuf);
            }
            switch (poll.type) {
                case NUMERIC -> NumericPoll.serializer.serialize((NumericPoll) poll, byteBuf);
                case DISCRETE -> DiscretePoll.serializer.serialize((DiscretePoll) poll, byteBuf);
                default -> throw new RuntimeException("Unknown poll type: " + poll.type);
            }
        }

        @Override
        public Poll deserialize(ByteBuf byteBuf) throws IOException {
            Type type = Type.valueOf(byteBuf.readShort());

            return switch (type) {
                case NUMERIC -> NumericPoll.serializer.deserialize(byteBuf);
                case DISCRETE -> DiscretePoll.serializer.deserialize(byteBuf);
            };
        }
    };

}
