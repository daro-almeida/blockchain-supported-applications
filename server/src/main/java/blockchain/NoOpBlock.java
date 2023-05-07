package blockchain;

import blockchain.requests.ClientRequest;

import java.util.Collections;
import java.util.List;

public class NoOpBlock extends Block {

    public NoOpBlock(int replicaId) {
        super(null,-1, -1, Collections.emptyList(), replicaId);

    }
}
