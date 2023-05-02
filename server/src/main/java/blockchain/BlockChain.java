package blockchain;

import java.util.HashMap;
import java.util.Map;

public class BlockChain {

    private final Map<Integer, Block> blocks = new HashMap<>();
    
    public BlockChain() {
    }

    public Block getBlock(int n){
        return blocks.get(n);
    }

    public void addBlock(int n, Block block){
        blocks.put(n, block);
    }

}
