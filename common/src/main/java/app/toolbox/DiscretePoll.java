package app.toolbox;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Set;

public class DiscretePoll extends Poll{

    private final Set<String> values;

    public DiscretePoll(String description, int maxParticipants, Set<PublicKey> authorized, Set<String> values) {
        super(Type.DISCRETE, description, maxParticipants, authorized);
        this.values = values;
    }

    public DiscretePoll(String description, int maxParticipants, Set<String> values) {
        super(Type.DISCRETE, description, maxParticipants);
        this.values = values;
    }

    public Set<String> getValues() {
        return values;
    }

    @Override
    public boolean validVote(Object vote) {
        // vote should be String
        if (vote instanceof String voteString) {
            return values.contains(voteString);
        }
        return false;
    }

    public static final ISerializer<DiscretePoll> serializer = new ISerializer<>() {
        @Override
        public void serialize(DiscretePoll poll, ByteBuf out) throws IOException {
            out.writeInt(poll.values.size());
            for (String value : poll.values)
                Utils.stringSerializer.serialize(value, out);
        }

        @Override
        public DiscretePoll deserialize(ByteBuf in) throws IOException {
            Authorization authorization = Authorization.valueOf(in.readShort());
            String description = Utils.stringSerializer.deserialize(in);
            int maxParticipants = in.readInt();
            Set<PublicKey> authorized = null;
            if (authorization == Authorization.CLOSED) {
                int size = in.readInt();
                authorized = new java.util.HashSet<>(size);
                for (int i = 0; i < size; i++)
                    authorized.add(Utils.rsaPublicKeySerializer.deserialize(in));
            }

            int size = in.readInt();
            Set<String> values = new java.util.HashSet<>(size);
            for (int i = 0; i < size; i++)
                values.add(Utils.stringSerializer.deserialize(in));

            if (authorization == Authorization.CLOSED)
                return new DiscretePoll(description, maxParticipants, authorized, values);
            else
                return new DiscretePoll(description, maxParticipants, authorized, values);
        }
    };
}

