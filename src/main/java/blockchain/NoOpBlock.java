package blockchain;

import blockchain.requests.ClientRequest;

import java.util.Collections;
import java.util.List;

public class NoOpBlock extends Block {

    //TODO not sure
    public NoOpBlock(byte[] previousHash, int consensusSeqN, int replicaId) {
        super(previousHash, -1, consensusSeqN, Collections.emptyList(), replicaId);
    }
}
