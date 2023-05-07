package blockchain;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import blockchain.requests.ClientRequest;
import io.netty.buffer.Unpooled;

public class BlockChain {

    private final long maxOps;

    // SEQ_N -> Block
    private final Map<Integer, Block> blocks = new HashMap<>();
    // consensusSeqN -> Block
    // nr de seq difninda no consensus
    private final Map<Integer, Block> consensusBlocks = new HashMap<>();
    // blockachain ops Set<UUID>
    private final Set<UUID> operations = new HashSet<>();

    public BlockChain(long maxOps) {
        this.maxOps = maxOps;
        // TODO insert genesis block
        blocks.put(0, new GenesisBlock());
        consensusBlocks.put(0, new GenesisBlock());
    }

    public Block getBlock(int n) {
        return blocks.get(n);
    }

    public Block newBlock(List<ClientRequest> ops, int replicaId) {
        //get last block contents with new ops
        return new Block(blocks.get(blocks.size() - 1).getHash(), ops, replicaId);
    }

    // seqn do consensus
    public void addBlock(int n, Block block) {
        // para todas as ops do bloco colocar no set de ops
        block.getOperations().forEach(req -> operations.add(req.getRequestId()));

        consensusBlocks.put(n, block);
        blocks.put(blocks.size(), block);
    }

    // check if block is valid
    // verificar hash dos anteriroes
    public boolean validateBlock(byte[] block) {
        Block b;
        try {
            b = Block.serializer.deserialize(Unpooled.wrappedBuffer(block));
            // rehash and check if hash is equal
            byte[] rehash = b.blockContentsWithoutHash();
            boolean validPrevHash = b.getPreviousHash().equals(blocks.get(blocks.size()-1).getHash());
            int numOps = b.getOperations().size();
            //TODO fkd up
            boolean validOps = b.getOperations().stream().map(r->r.getRequestId()).allMatch(id -> !operations.contains(id));
            if (!validPrevHash || !rehash.equals(b.getHash()) || (numOps > 1 && numOps <= maxOps) || validOps)
                return false;

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean validOperation(ClientRequest req){
        return req.checkSignature() && operations.contains(req.getRequestId());        
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


}
