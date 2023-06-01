package app.toolbox.messages;

import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class DiscreteVote extends Vote<Integer> {

    public DiscreteVote(UUID rid, PublicKey clientID, UUID pollID, int valueIndex){
        super(Poll.Type.DISCRETE, rid, clientID, pollID, valueIndex);
    }

    public static final ISerializer<Integer> valueSerializer = new ISerializer<>() {

        @Override
        public void serialize(Integer value, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(value);
        }

        @Override
        public Integer deserialize(ByteBuf byteBuf) throws IOException {
            return byteBuf.readInt();
        }
    };
}
