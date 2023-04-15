package consensus.messages;

import consensus.PreparedProof;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ViewChangeMessage extends SignedProtoMessage {

    public final static short MESSAGE_ID = 104;

    private final int newViewNumber, nodeId, lastExecuted;

    // proofs this node prepared requests with seqNum > n
    private final Map<Integer, PreparedProof> preparedProofs;

    public ViewChangeMessage(int newViewNumber, int lastExecuted, Map<Integer, PreparedProof> preparedProofs, int nodeId) {
        super(MESSAGE_ID);
        this.newViewNumber = newViewNumber;
        this.lastExecuted = lastExecuted;
        this.preparedProofs = preparedProofs;
        this.nodeId = nodeId;
    }

    public int getNewViewNumber() {
        return newViewNumber;
    }

    public int getLastExecuted() {
        return lastExecuted;
    }

    public int maxPrepared() {
        return preparedProofs.keySet().stream().max(Integer::compareTo).orElse(-1);
    }

    public PreparedProof getPreparedProof(int seqNum) {
        return preparedProofs.get(seqNum);
    }

    public int getNodeId() {
        return nodeId;
    }

    public boolean preparedProofsValid(int f, Map<Integer, PublicKey> publicKeys) {
        for (PreparedProof proof : preparedProofs.values()) {
            if (!proof.isValid(f, publicKeys))
                return false;
        }
        return true;
    }

    public Map<Integer, PreparedProof> getPreparedProofs() {
        return preparedProofs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViewChangeMessage that = (ViewChangeMessage) o;
        return getNewViewNumber() == that.getNewViewNumber() && getNodeId() == that.getNodeId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNewViewNumber(), getNodeId());
    }

    public static final SignedMessageSerializer<ViewChangeMessage> serializer = new SignedMessageSerializer<ViewChangeMessage>() {
        @Override
        public void serializeBody(ViewChangeMessage viewChangeMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(viewChangeMessage.getNewViewNumber());
            byteBuf.writeInt(viewChangeMessage.getNodeId());
            byteBuf.writeInt(viewChangeMessage.getLastExecuted());
            byteBuf.writeInt(viewChangeMessage.preparedProofs.size());
            for (Map.Entry<Integer, PreparedProof> entry : viewChangeMessage.preparedProofs.entrySet()) {
                byteBuf.writeInt(entry.getKey());
                PreparedProof.serializer.serialize(entry.getValue(), byteBuf);
            }
        }

        @Override
        public ViewChangeMessage deserializeBody(ByteBuf byteBuf) throws IOException {
            int newViewNumber = byteBuf.readInt();
            int nodeId = byteBuf.readInt();
            int lastExecuted = byteBuf.readInt();
            int size = byteBuf.readInt();
            Map<Integer, PreparedProof> preparedProofs = new HashMap<>();
            for (int i = 0; i < size; i++) {
                int seqNum = byteBuf.readInt();
                PreparedProof preparedProof = PreparedProof.serializer.deserialize(byteBuf);
                preparedProofs.put(seqNum, preparedProof);
            }
            return new ViewChangeMessage(newViewNumber, lastExecuted, preparedProofs, nodeId);
        }
    };


    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }

}
