package blockchain;

import java.util.Collections;

public class GenesisBlock extends Block {

    public GenesisBlock(int replicaId) {
        super(null, 0,0, Collections.emptyList(), replicaId);
    }
}
