package consensus.messages;

import consensus.requests.ProposeRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PullRequestsReplyMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 107;

    private final Map<Integer, ProposeRequest> requests;
    private final int nodeId;

    public PullRequestsReplyMessage(Map<Integer, ProposeRequest> requests, int nodeId) {
        super(MESSAGE_ID);
        this.requests = requests;
        this.nodeId = nodeId;
    }

    public Map<Integer, ProposeRequest> getRequests() {
        return requests;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<PullRequestsReplyMessage> serializer = new SignedMessageSerializer<PullRequestsReplyMessage>() {
        @Override
        public void serializeBody(PullRequestsReplyMessage pull, ByteBuf out) throws IOException {
            out.writeInt(pull.requests.size());
            for (Map.Entry<Integer, ProposeRequest> entry : pull.requests.entrySet()) {
                out.writeInt(entry.getKey());
                ProposeRequest.serializer.serialize(entry.getValue(), out);
            }
            out.writeInt(pull.nodeId);
        }

        @Override
        public PullRequestsReplyMessage deserializeBody(ByteBuf in) throws IOException {
            int size = in.readInt();
            Map<Integer, ProposeRequest> requests = new HashMap<>();
            for (int i = 0; i < size; i++) {
                int seq = in.readInt();
                ProposeRequest request = ProposeRequest.serializer.deserialize(in);
                requests.put(seq, request);
            }
            int nodeId = in.readInt();
            return new PullRequestsReplyMessage(requests, nodeId);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }
}
