package consensus;

import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PreparedProof implements Proof {

    // pre-prepare for the associated request (doesn't need to have the request itself)
    private final PrePrepareMessage prePrepare;
    // set of prepares for the associated request
    private final Set<PrepareMessage> prepares;

    public PreparedProof(PrePrepareMessage prePrepare, Set<PrepareMessage> prepares) {
        this.prePrepare = prePrepare;
        this.prepares = prepares;
    }

    public PrePrepareMessage getPrePrepare() {
        return prePrepare;
    }

    @Override
    public boolean isValid(int f, Map<Integer, PublicKey> publicKeys) {
        return checkSignatures(publicKeys) && PBFTPredicates.prepared(f, prePrepare, prepares);
    }

    private boolean checkSignatures(Map<Integer, PublicKey> publicKeys) {
        return prepares.stream().allMatch(p -> Crypto.checkSignature(p, publicKeys.get(p.getNodeId())));
    }

    public static final ISerializer<PreparedProof> serializer = new ISerializer<PreparedProof>() {
        @Override
        public void serialize(PreparedProof proof, ByteBuf out) throws IOException {
            PrePrepareMessage.serializer.serialize(proof.prePrepare, out);
            out.writeInt(proof.prepares.size());
            for (PrepareMessage p : proof.prepares) {
                PrepareMessage.serializer.serialize(p, out);
            }
        }

        @Override
        public PreparedProof deserialize(ByteBuf in) throws IOException {
            PrePrepareMessage prePrepare = PrePrepareMessage.serializer.deserialize(in);
            int preparesSize = in.readInt();
            Set<PrepareMessage> prepares = new HashSet<>(preparesSize);
            for (int i = 0; i < preparesSize; i++) {
                prepares.add(PrepareMessage.serializer.deserialize(in));
            }
            return new PreparedProof(prePrepare, prepares);
        }
    };
}
