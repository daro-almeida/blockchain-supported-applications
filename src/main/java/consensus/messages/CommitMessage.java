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
    private final String cryptoName;

    public CommitMessage(PrepareMessage msg, String cryptoName) {
        this(msg.getViewNumber(), msg.getSeq(), msg.getDigest(), cryptoName);
    }

    public CommitMessage(int viewNumber, int seq, byte[] digest, String cryptoName) {
        super(CommitMessage.MESSAGE_ID);

        this.viewNumber = viewNumber;
        this.seq = seq;
        this.digest = digest;
        this.cryptoName = cryptoName;
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

    public String getCryptoName() {
    	return cryptoName;
    }

    public static final SignedMessageSerializer<CommitMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(CommitMessage signedProtoMessage, ByteBuf out) throws IOException {
            out.writeInt(signedProtoMessage.viewNumber);
            out.writeInt(signedProtoMessage.seq);
            Utils.byteArraySerializer.serialize(signedProtoMessage.digest, out);
            Utils.stringSerializer.serialize(signedProtoMessage.cryptoName, out);
        }

        @Override
        public CommitMessage deserializeBody(ByteBuf in) throws IOException {
            int viewN = in.readInt();
            int seq = in.readInt();
            byte[] digest = Utils.byteArraySerializer.deserialize(in);
            String cryptoName = Utils.stringSerializer.deserialize(in);
            return new CommitMessage(viewN, seq, digest, cryptoName);
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommitMessage that = (CommitMessage) o;
        return getViewNumber() == that.getViewNumber() && getSeq() == that.getSeq() && Arrays.equals(getDigest(), that.getDigest()) && getCryptoName().equals(that.getCryptoName());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getViewNumber(), getSeq(), getCryptoName());
        result = 31 * result + Arrays.hashCode(getDigest());
        return result;
    }

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
