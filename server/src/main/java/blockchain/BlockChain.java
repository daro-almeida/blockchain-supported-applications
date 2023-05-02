package blockchain;

import java.util.HashMap;
import java.util.Map;

public class BlockChain {

    //seqn -> block
    private final Map<Integer, Block> blocks = new HashMap<>();
    //consensus_seqn -> block
    //Set<UUID>

    public BlockChain() {
        //TODO insert genesis block
    }

    public Block getBlock(int n){
        return blocks.get(n);
    }

    public void addBlock(int n, Block block){
        blocks.put(n, block);
    }

}
