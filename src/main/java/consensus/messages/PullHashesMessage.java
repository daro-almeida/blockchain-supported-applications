package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PullHashesMessage extends SignedProtoMessage {

    public static final short MESSAGE_ID = 108;

    private final Set<Integer> neededHashes;
    private final int nodeId;

    public PullHashesMessage(Set<Integer> neededHashes, int nodeId) {
        super(MESSAGE_ID);
        this.neededHashes = neededHashes;
        this.nodeId = nodeId;
    }

    public Set<Integer> getNeededHashes() {
        return neededHashes;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<PullHashesMessage> serializer = new SignedMessageSerializer<>() {
        @Override
        public void serializeBody(PullHashesMessage pull, ByteBuf out) throws IOException {
            out.writeInt(pull.neededHashes.size());
            for (int seq : pull.neededHashes) {
                out.writeInt(seq);
            }
            out.writeInt(pull.nodeId);
        }

        @Override
        public PullHashesMessage deserializeBody(ByteBuf in) throws IOException {
            int size = in.readInt();
            var seqs = new HashSet<Integer>(size);
            for (int i = 0; i < size; i++) {
                seqs.add(in.readInt());
            }
            int nodeId = in.readInt();
            return new PullHashesMessage(seqs, nodeId);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }
}
