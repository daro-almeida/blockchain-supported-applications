package blockchain;

import java.util.Collections;

public class GenesisBlock extends Block {

    public GenesisBlock(int replicaId) {
        super(null, Collections.emptyList(), -1);
    }
}
