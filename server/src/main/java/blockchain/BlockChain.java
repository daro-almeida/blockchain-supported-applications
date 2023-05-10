package blockchain;

import java.util.*;

import blockchain.requests.ClientRequest;
import utils.Crypto;

public class BlockChain {

    private final long maxOpsPerBlock;

    // seqn -> Block
    private final Map<Integer, Block> blocks = new HashMap<>();
    // consensusSeqN (seqn which block was decided in consensus algorithm) -> Block
    private final Map<Integer, Block> consensusBlocks = new HashMap<>();
    // blockchain ops Set<UUID>
    private final Set<UUID> operations = new HashSet<>();

    public BlockChain(long maxOpsPerBlock) {
        this.maxOpsPerBlock = maxOpsPerBlock;
        blocks.put(0, new GenesisBlock());
        consensusBlocks.put(0, new GenesisBlock());
    }

    public Block getBlockByConsensusSeq(int seq) {
        return consensusBlocks.get(seq);
    }

    public Block newBlock(List<ClientRequest> ops, int replicaId) {
        //get last block contents with new ops
        return new Block(blocks.get(blocks.size() - 1).getHash(), ops, replicaId);
    }

    /*
     * Parameter is sequence number from consensus
     */
    public void addBlock(int seq, Block block) {
        // para todas as ops do bloco colocar no set de ops
        block.getOperations().forEach(req -> operations.add(req.getRequestId()));

        consensusBlocks.put(seq, block);
        blocks.put(blocks.size(), block);
    }

    public boolean validateBlock(Block block) {
        // rehash and check if hash is equal
        byte[] rehash = Crypto.digest(block.blockContentsWithoutHash());
        boolean validPrevHash = Arrays.equals(block.getPreviousHash(), blocks.get(blocks.size() - 1).getHash());
        int numOps = block.getOperations().size();
        boolean validOps = block.getOperations().stream().map(ClientRequest::getRequestId).noneMatch(operations::contains);
        //Check ops signatures
        //TODO missing: check op signatures and check if there are repeated ops inside block; optimize
        boolean signedOps = block.getOperations().stream().filter(ClientRequest::checkSignature).count() == block.getOperations().size();
        return validPrevHash && Arrays.equals(rehash, block.getHash()) && (numOps < 1 || numOps > maxOpsPerBlock) && !validOps && signedOps;
    }

    public boolean containsOperation(ClientRequest op) {
        return operations.contains(op.getRequestId());
    }

    public boolean containsOperation(UUID opId) {
        return operations.contains(opId);
    }
}
