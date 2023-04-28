package consensus;

import consensus.messages.CommitMessage;
import consensus.messages.PrePrepareMessage;
import consensus.messages.PrepareMessage;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;

import java.io.IOException;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// proof the replica has (or could have) committed the associated request (submitted to the protocol above)
public class CommittedProof implements Proof {

    // pre-prepare for the associated request (doesn't need to have the request itself)
    private final PrePrepareMessage prePrepare;
    // set of prepares for the associated request
    private final Set<PrepareMessage> prepares;
    // set of commits for the associated request
    private final Set<CommitMessage> commits;

    public CommittedProof(PrePrepareMessage prePrepare, Set<PrepareMessage> prepares, Set<CommitMessage> commits) {
        this.prePrepare = prePrepare;
        this.prepares = prepares;
        this.commits = commits;
    }

    public int getSeq() {
        return prePrepare.getSeq();
    }

    public boolean isValid(int f, Map<Integer, PublicKey> publicKeys) {
        return checkSignatures(publicKeys) && PBFTPredicates.committed(f, prePrepare, prepares, commits);
    }

    private boolean checkSignatures(Map<Integer, PublicKey> publicKeys) {
        return prepares.stream().allMatch(p -> Crypto.checkSignature(p, publicKeys.get(p.getNodeId()))) &&
                commits.stream().allMatch(c -> Crypto.checkSignature(c, publicKeys.get(c.getNodeId())));
    }

    public static final ISerializer<CommittedProof> serializer = new ISerializer<>() {
        @Override
        public void serialize(CommittedProof committedProof, ByteBuf byteBuf) throws IOException {
            PrePrepareMessage.serializer.serialize(committedProof.prePrepare, byteBuf);
            byteBuf.writeInt(committedProof.prepares.size());
            for (PrepareMessage p : committedProof.prepares)
                PrepareMessage.serializer.serialize(p, byteBuf);
            byteBuf.writeInt(committedProof.commits.size());
            for (CommitMessage c : committedProof.commits)
                CommitMessage.serializer.serialize(c, byteBuf);
        }

        @Override
        public CommittedProof deserialize(ByteBuf byteBuf) throws IOException {
            PrePrepareMessage prePrepare = PrePrepareMessage.serializer.deserialize(byteBuf);
            int preparesSize = byteBuf.readInt();
            Set<PrepareMessage> prepares = new HashSet<>(preparesSize);
            for (int i = 0; i < preparesSize; i++)
                prepares.add(PrepareMessage.serializer.deserialize(byteBuf));
            int commitsSize = byteBuf.readInt();
            Set<CommitMessage> commits = new HashSet<>(commitsSize);
            for (int i = 0; i < commitsSize; i++)
                commits.add(CommitMessage.serializer.deserialize(byteBuf));
            return new CommittedProof(prePrepare, prepares, commits);
        }
    };

}