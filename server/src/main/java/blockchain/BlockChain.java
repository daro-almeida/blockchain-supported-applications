package blockchain;

import java.util.*;

import blockchain.requests.ClientRequest;
import metrics.Metrics;
import utils.Crypto;
import utils.Utils;

public class BlockChain {

    private final long maxOpsPerBlock;

    // seqn -> Block
    private final Map<Integer, Block> blocks = new HashMap<>();
    // consensusSeqN (seqn which block was decided in consensus algorithm) -> Block
    private final Map<Integer, Block> consensusBlocks = new HashMap<>();
    // blockchain ops Set<UUID>
    private final Set<UUID> operations = new HashSet<>();
    private Block lastPendingBlock = null;

    public BlockChain(long maxOpsPerBlock) {
        this.maxOpsPerBlock = maxOpsPerBlock;
        blocks.put(0, new GenesisBlock());
        consensusBlocks.put(0, new GenesisBlock());
    }

    public Block getBlockByConsensusSeq(int seq) {
        return consensusBlocks.get(seq);
    }

    public Block newBlock(List<ClientRequest> ops, int replicaId) {
        // get last block contents with new ops
        if (lastPendingBlock != null)
            lastPendingBlock = new Block(lastPendingBlock.getHash(), ops, replicaId);
        else
            lastPendingBlock = new Block(blocks.get(blocks.size() - 1).getHash(), ops, replicaId);
        return lastPendingBlock;
    }

    /*
     * Parameter is sequence number from consensus.
     */
    public int addBlock(int seq, Block block) {
        assert block.getSignature() != null;

        Metrics.writeMetric("committed_block", "hash", Utils.bytesToHex(block.getHash()),
                "seq", String.valueOf(blocks.size()));

        if (lastPendingBlock != null && lastPendingBlock.equals(block))
            lastPendingBlock = null;

        block.getOperations().forEach(req -> operations.add(req.getRequestId()));

        consensusBlocks.put(seq, block);
        blocks.put(blocks.size(), block);
        return blocks.size();
    }

    public void validateBlock(Block block) throws InvalidBlockException {
        // rehash and check if hash is equal

        int numOps = block.getOperations().size();
        if (numOps < 1 || numOps > maxOpsPerBlock)
            throw new InvalidBlockException("Invalid number of operations: " + numOps);

        Block lastBlock = blocks.get(blocks.size() - 1);
        if (!Arrays.equals(block.getPreviousHash(), lastBlock.getHash()))
            throw new InvalidBlockException("Previous hash does not match");

        if (block.getOperations().stream().map(ClientRequest::getRequestId).anyMatch(operations::contains))
            throw new InvalidBlockException("Block contains already existing operations");

        // check if block itself has repeated ops
        Set<UUID> ops = new HashSet<>();
        for (ClientRequest op : block.getOperations()) {
            if (ops.contains(op.getRequestId()))
                throw new InvalidBlockException("Block contains repeated operations");
            ops.add(op.getRequestId());
        }

        byte[] rehash = Crypto.digest(block.blockContentsWithoutHash());
        if (!Arrays.equals(rehash, block.getHash()))
            throw new InvalidBlockException("Block hash does not match");

        if (!block.getOperations().stream().allMatch(ClientRequest::checkSignature))
            throw new InvalidBlockException("Block contains invalid request signatures");
    }

    public boolean containsOperation(ClientRequest op) {
        return operations.contains(op.getRequestId());
    }

    public boolean containsOperation(UUID opId) {
        return operations.contains(opId);
    }
}
