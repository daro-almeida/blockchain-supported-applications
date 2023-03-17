package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class CommitMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 103;

    private int viewN, seq;
    private byte[] digest;
    private Host sender;


    public CommitMessage() {
        super(CommitMessage.MESSAGE_ID);
    }

    public int getViewN() {
        return viewN;
    }

    public int getSeq() {
        return seq;
    }

    public byte[] getDigest() {
        return digest;
    }

    public Host getSender() {
        return sender;
    }

    public static final SignedMessageSerializer<CommitMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(CommitMessage signedProtoMessage, ByteBuf out) throws IOException {
            out.writeInt(signedProtoMessage.viewN);
            out.writeInt(signedProtoMessage.seq);
            out.writeInt(signedProtoMessage.digest.length);
            out.writeBytes(signedProtoMessage.digest);
            Host.serializer.serialize(signedProtoMessage.sender, out);
        }

        @Override
        public CommitMessage deserializeBody(ByteBuf in) throws IOException {
            CommitMessage msg = new CommitMessage();
            msg.viewN = in.readInt();
            msg.seq = in.readInt();
            msg.digest = new byte[in.readInt()];
            in.readBytes(msg.digest);
            msg.sender = Host.serializer.deserialize(in);
            return msg;
        }
    };

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
