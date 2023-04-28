package consensus.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PullHashesReplyMessage extends SignedProtoMessage {

    public static final short MESSAGE_ID = 109;

    private final Map<Integer, byte[]> hashes;
    private final int nodeId;

    public PullHashesReplyMessage(Map<Integer, byte[]> hashes, int nodeId) {
        super(MESSAGE_ID);
        this.hashes = hashes;
        this.nodeId = nodeId;
    }

    public Map<Integer, byte[]> getHashes() {
        return hashes;
    }

    public int getNodeId() {
        return nodeId;
    }

    public static final SignedMessageSerializer<PullHashesReplyMessage> serializer = new SignedMessageSerializer<>() {
        @Override
        public void serializeBody(PullHashesReplyMessage pull, ByteBuf out) throws IOException {
            out.writeInt(pull.hashes.size());
            for (Map.Entry<Integer, byte[]> entry : pull.hashes.entrySet()) {
                out.writeInt(entry.getKey());
                Utils.byteArraySerializer.serialize(entry.getValue(), out);
            }
            out.writeInt(pull.nodeId);
        }

        @Override
        public PullHashesReplyMessage deserializeBody(ByteBuf in) throws IOException {
            int size = in.readInt();
            var hashes = new HashMap<Integer, byte[]>(size);
            for (int i = 0; i < size; i++) {
                int seq = in.readInt();
                byte[] hash = Utils.byteArraySerializer.deserialize(in);
                hashes.put(seq, hash);
            }
            int nodeId = in.readInt();
            return new PullHashesReplyMessage(hashes, nodeId);
        }
    };

    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return null;
    }
}
