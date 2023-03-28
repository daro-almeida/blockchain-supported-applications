package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class CommitMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 103;

    private final int viewNumber, seq;
    private final byte[] digest;
    private final int nodeId;

    public CommitMessage(PrepareMessage msg, int nodeId) {
        this(msg.getViewNumber(), msg.getSeq(), msg.getDigest(), nodeId);
    }

    public CommitMessage(int viewNumber, int seq, byte[] digest, int nodeId) {
        super(CommitMessage.MESSAGE_ID);

        this.viewNumber = viewNumber;
        this.seq = seq;
        this.digest = digest;
        this.nodeId = nodeId;
    }

    public int getViewNumber() {
        return viewNumber;
    }

    public int getSeq() {
        return seq;
    }

    public byte[] getDigest() {
        return digest;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<CommitMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(CommitMessage signedProtoMessage, ByteBuf out) throws IOException {
            out.writeInt(signedProtoMessage.viewNumber);
            out.writeInt(signedProtoMessage.seq);
            Utils.byteArraySerializer.serialize(signedProtoMessage.digest, out);
            out.writeInt(signedProtoMessage.nodeId);
        }

        @Override
        public CommitMessage deserializeBody(ByteBuf in) throws IOException {
            int viewN = in.readInt();
            int seq = in.readInt();
            byte[] digest = Utils.byteArraySerializer.deserialize(in);
            int id = in.readInt();
            return new CommitMessage(viewN, seq, digest, id);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommitMessage that = (CommitMessage) o;
        return getViewNumber() == that.getViewNumber() && getSeq() == that.getSeq() && getNodeId() == that.getNodeId() && Arrays.equals(getDigest(), that.getDigest());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getViewNumber(), getSeq(), getNodeId());
        result = 31 * result + Arrays.hashCode(getDigest());
        return result;
    }

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
