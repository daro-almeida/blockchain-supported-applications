package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.Objects;

public class CommitMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 103;

    private final int viewN, seq;
    private final byte[] digest;
    private final String cryptoName;


    public CommitMessage(int viewN, int seq, byte[] digest, String cryptoName) {
        super(CommitMessage.MESSAGE_ID);

        this.viewN = viewN;
        this.seq = seq;
        this.digest = digest;
        this.cryptoName = cryptoName;
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

    public String getCryptoName() {
    	return cryptoName;
    }

    public static final SignedMessageSerializer<CommitMessage> serializer = new SignedMessageSerializer<>() {

        @Override
        public void serializeBody(CommitMessage signedProtoMessage, ByteBuf out) throws IOException {
            out.writeInt(signedProtoMessage.viewN);
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
        return getViewN() == that.getViewN() && getSeq() == that.getSeq() && getCryptoName().equals(that.getCryptoName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getViewN(), getSeq(), getCryptoName());
    }

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
