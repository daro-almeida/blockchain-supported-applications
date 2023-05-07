package blockchain;

import java.util.Collections;

public class GenesisBlock extends Block {

    public GenesisBlock() {
        super(null, Collections.emptyList(), -1);
    }
}
