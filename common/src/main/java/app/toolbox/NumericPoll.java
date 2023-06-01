package app.toolbox;

import app.toolbox.messages.Vote;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Set;

public class NumericPoll extends Poll{

    private final double min;
    private final double max;

    public NumericPoll(String description, int maxParticipants, Set<PublicKey> authorized, double min, double max) {
        super(Type.NUMERIC, description, maxParticipants, authorized);
        this.min = min;
        this.max = max;
    }

    public NumericPoll(String description, int maxParticipants, double min, double max) {
        super(Type.NUMERIC, description, maxParticipants);
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    @Override
    public boolean validVote(Vote<?> vote) {
        // vote should be double
        if (vote.getValue() instanceof Double voteDouble) {
            return voteDouble >= min && voteDouble <= max;
        }
        return false;
    }

    public static final ISerializer<NumericPoll> serializer = new ISerializer<NumericPoll>() {
        @Override
        public void serialize(NumericPoll poll, ByteBuf out) throws IOException {
            out.writeDouble(poll.min);
            out.writeDouble(poll.max);
        }

        @Override
        public NumericPoll deserialize(ByteBuf in) throws IOException {
            Authorization authorization = Authorization.valueOf(in.readByte());
            String description = Utils.stringSerializer.deserialize(in);
            int maxParticipants = in.readInt();
            Set<PublicKey> authorized = null;
            if (authorization == Authorization.CLOSED) {
                int size = in.readInt();
                authorized = new java.util.HashSet<>(size);
                for (int i = 0; i < size; i++)
                    authorized.add(Utils.rsaPublicKeySerializer.deserialize(in));
            }

            double min = in.readDouble();
            double max = in.readDouble();

            if (authorized != null)
                return new NumericPoll(description, maxParticipants, authorized, min, max);
            else
                return new NumericPoll(description, maxParticipants, min, max);
        }
    };
}
