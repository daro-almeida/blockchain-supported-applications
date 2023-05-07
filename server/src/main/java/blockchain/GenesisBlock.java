package blockchain;

import java.util.Collections;

public class GenesisBlock extends Block {

    public GenesisBlock() {
        super(null,-1, -1, Collections.emptyList(), -1);
    }
}
