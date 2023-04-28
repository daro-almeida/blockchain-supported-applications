package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PullRequestsMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 106;

    private final Set<Integer> neededRequests;
    private final int nodeId;

    public PullRequestsMessage(Set<Integer> neededRequests, int nodeId) {
        super(MESSAGE_ID);
        this.neededRequests = neededRequests;
        this.nodeId = nodeId;
    }

    public Set<Integer> getNeededRequests() {
        return neededRequests;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<PullRequestsMessage> serializer = new SignedMessageSerializer<PullRequestsMessage>() {
        @Override
        public void serializeBody(PullRequestsMessage pull, ByteBuf out) throws IOException {
            out.writeInt(pull.neededRequests.size());
            for (int seq : pull.neededRequests) {
                out.writeInt(seq);
            }
            out.writeInt(pull.nodeId);
        }

        @Override
        public PullRequestsMessage deserializeBody(ByteBuf in) throws IOException {
            int size = in.readInt();
            var seqs = new HashSet<Integer>(size);
            for (int i = 0; i < size; i++) {
                seqs.add(in.readInt());
            }
            int nodeId = in.readInt();
            return new PullRequestsMessage(seqs, nodeId);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }
}
