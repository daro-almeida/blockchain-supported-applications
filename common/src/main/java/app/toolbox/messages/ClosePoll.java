package app.toolbox.messages;

import app.WriteOperation;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Utils;

import java.io.IOException;
import java.security.PublicKey;
import java.util.UUID;

public class ClosePoll extends WriteOperation{

    public final static short MESSAGE_ID = 703;

    private final UUID rid;
    private final PublicKey clientID;

    private final UUID pollID;


    public ClosePoll(UUID rid, PublicKey pk, UUID pollID){
        super(ClosePoll.MESSAGE_ID);

        this.rid = rid;
        this.clientID = pk;
        this.pollID = pollID;
    }


    public UUID getRid() {
        return rid;
    }


    public PublicKey getClientID() {
        return clientID;
    }


    public UUID getPollID() {
        return pollID;
    }

    public static final ISerializer<ClosePoll> serializer = new ISerializer<>() {

        @Override
        public void serialize(ClosePoll closePoll, ByteBuf byteBuf) throws IOException {
            Utils.uuidSerializer.serialize(closePoll.rid, byteBuf);
            Utils.rsaPublicKeySerializer.serialize(closePoll.clientID, byteBuf);
            Utils.uuidSerializer.serialize(closePoll.pollID, byteBuf);
        }

        @Override
        public ClosePoll deserialize(ByteBuf byteBuf) throws IOException {
            UUID rid = Utils.uuidSerializer.deserialize(byteBuf);
            PublicKey clientID = Utils.rsaPublicKeySerializer.deserialize(byteBuf);
            UUID pollID = Utils.uuidSerializer.deserialize(byteBuf);
            return new ClosePoll(rid, clientID, pollID);
        }
    };
}
