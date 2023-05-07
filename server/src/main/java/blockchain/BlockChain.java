package blockchain;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import blockchain.requests.ClientRequest;
import io.netty.buffer.Unpooled;

public class BlockChain {

    // SEQ_N -> Block
    private final Map<Integer, Block> blocks = new HashMap<>();
    // consensusSeqN -> Block
    // nr de seq difninda no consensus
    private final Map<Integer, Block> consensusBlocks = new HashMap<>();
    // blockachain ops Set<UUID>
    private final Set<UUID> operations = new HashSet<>();

    public BlockChain() {
        // TODO insert genesis block
        blocks.put(0, new GenesisBlock());
    }

    public Block getBlock(int n) {
        return blocks.get(n);
    }

    // nr do consensus
    public void addBlock(int n, Block block) {
        // para todas as ops do bloco colocar no set de ops
        block.getOperations().forEach(req -> operations.add(req.getRequestId()));
        // get last block from consensusBlocks
        Block last = consensusBlocks.get(block.getConsensusSeqN() - 1);
        // set block prevHash with last hash
        //block.setPreviousHash(last.getHash());
        
        blocks.put(n, block);
    }

    // check if block is valid
    // verificar hash dos anteriroes
    public boolean validateBlock(byte[] block) {
        Block b;
        try {
            b = Block.serializer.deserialize(Unpooled.wrappedBuffer(block));
            return b.getPreviousHash().equals(blocks.get(b.getSeqN() - 1).getHash());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    // check if blocks are valid
    public boolean validBlocks() {
        Block prev = null;
        for (Block block : blocks.values()) {
            if (prev != null && !block.getPreviousHash().equals(prev.getHash())) {
                return false;
            }
            prev = block;
        }
        return true;
    }

    public boolean containsRequest(ClientRequest req) {
        return operations.contains(req.getRequestId());
    }

    public boolean containsRequest(UUID req) {
        return operations.contains(req);
    }

    public boolean containsBlock(Block block) {
        return blocks.containsKey(block.getSeqN()) ||
                consensusBlocks.containsKey(block.getConsensusSeqN());
    }

}
