package consensus.messages;

import consensus.CommittedProof;
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

    // proof this node committed requests up to lastExecuted
    private final CommittedProof committedProof;

    // proofs this node prepared requests with seqNum > n
    private final Map<Integer, PreparedProof> preparedProofs;

    public ViewChangeMessage(int newViewNumber, int lastExecuted,
                             CommittedProof committedProof, Map<Integer, PreparedProof> preparedProofs, int nodeId) {
        super(MESSAGE_ID);
        this.newViewNumber = newViewNumber;
        this.lastExecuted = lastExecuted;
        this.committedProof = committedProof;
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
        return preparedProofs.keySet().stream().max(Integer::compareTo).orElse(0);
    }

    public PreparedProof getPreparedProof(int seqNum) {
        return preparedProofs.get(seqNum);
    }

    public int getNodeId() {
        return nodeId;
    }

    private boolean preparedProofsValid(int f, Map<Integer, PublicKey> publicKeys) {
        // key matches seq in proof and proof is valid
        return preparedProofs.entrySet().stream()
                .allMatch(entry -> entry.getKey() == entry.getValue().getPrePrepare().getSeq() &&
                        entry.getValue().isValid(f, publicKeys));
    }

    public boolean isValid(int f, Map<Integer, PublicKey> publicKeys) {
        return ((lastExecuted == 0 && committedProof == null) ||
                (lastExecuted == committedProof.getSeq() && committedProof.isValid(f, publicKeys))) &&
                preparedProofsValid(f, publicKeys);
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
            CommittedProof.serializer.serialize(viewChangeMessage.committedProof, byteBuf);
            byteBuf.writeInt(viewChangeMessage.preparedProofs.size());
            for (Map.Entry<Integer, PreparedProof> entry : viewChangeMessage.preparedProofs.entrySet()) {
                PreparedProof.serializer.serialize(entry.getValue(), byteBuf);
            }
        }

        @Override
        public ViewChangeMessage deserializeBody(ByteBuf byteBuf) throws IOException {
            int newViewNumber = byteBuf.readInt();
            int nodeId = byteBuf.readInt();
            int lastExecuted = byteBuf.readInt();
            CommittedProof committedProof = CommittedProof.serializer.deserialize(byteBuf);
            int size = byteBuf.readInt();
            Map<Integer, PreparedProof> preparedProofs = new HashMap<>();
            for (int i = 0; i < size; i++) {
                PreparedProof preparedProof = PreparedProof.serializer.deserialize(byteBuf);
                int seqNum = preparedProof.getPrePrepare().getSeq();
                preparedProofs.put(seqNum, preparedProof);
            }
            return new ViewChangeMessage(newViewNumber, lastExecuted, committedProof, preparedProofs, nodeId);
        }
    };


    @Override
    public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
        return serializer;
    }

}
