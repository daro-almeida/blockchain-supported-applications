package app.toolbox.messages;

import app.toolbox.Poll;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class NumericVote extends Vote{

    private final double value;

    public NumericVote(UUID rid, PublicKey clientID, UUID pollID, double value){
        super(Poll.Type.NUMERIC, rid, clientID, pollID);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    public static final ISerializer<NumericVote> serializer = new ISerializer<>() {

        @Override
        public void serialize(NumericVote vote, ByteBuf byteBuf) throws IOException {
            byteBuf.writeDouble(vote.value);
        }

        @Override
        public NumericVote deserialize(ByteBuf byteBuf) throws IOException {
            UUID rid = Utils.uuidSerializer.deserialize(byteBuf);
            PublicKey clientID = Utils.rsaPublicKeySerializer.deserialize(byteBuf);
            UUID pollID = Utils.uuidSerializer.deserialize(byteBuf);
            double value = byteBuf.readDouble();
            return new NumericVote(rid, clientID, pollID, value);
        }
    };
    
}
