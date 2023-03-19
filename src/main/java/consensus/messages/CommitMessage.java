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
            out.writeInt(signedProtoMessage.digest.length);
            out.writeBytes(signedProtoMessage.digest);
            Utils.stringSerializer.serialize(signedProtoMessage.cryptoName, out);
        }

        @Override
        public CommitMessage deserializeBody(ByteBuf in) throws IOException {
            int viewN = in.readInt();
            int seq = in.readInt();
            int digestLength = in.readInt();
            byte[] digest = new byte[digestLength];
            in.readBytes(digest);
            String cryptoName = Utils.stringSerializer.deserialize(in);
            return new CommitMessage(viewN, seq, digest, cryptoName);
        }
    };

    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return CommitMessage.serializer;
    }

}
