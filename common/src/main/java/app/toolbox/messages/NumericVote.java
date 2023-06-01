package app.toolbox.messages;

import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class NumericVote extends Vote<Double>{

    public NumericVote(UUID rid, PublicKey clientID, UUID pollID, double value){
        super(Poll.Type.NUMERIC, rid, clientID, pollID, value);
    }

    public static final ISerializer<Double> valueSerializer = new ISerializer<>() {

        @Override
        public void serialize(Double value, ByteBuf byteBuf) throws IOException {
            byteBuf.writeDouble(value);
        }

        @Override
        public Double deserialize(ByteBuf byteBuf) throws IOException {
            return byteBuf.readDouble();
        }
    };
    
}
