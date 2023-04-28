package blockchain;

import blockchain.requests.ClientRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.SignaturesHelper;
import utils.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public class Block {

    private final byte[] hash;
    private final byte[] previousHash;
    private final int seqN;
    private final int consensusSeqN;
    private final int replicaId; // identity of the replica that generated the block and its signature.
    private final List<ClientRequest> operations;

    private byte[] signature;

    public Block(byte[] previousHash, int seqN, int consensusSeqN, List<ClientRequest> operations, int replicaId) {
        this.previousHash = previousHash;
        this.seqN = seqN;
        this.consensusSeqN = consensusSeqN;
        this.operations = operations;
        this.replicaId = replicaId;

        this.hash = generateHash();
    }

    private Block(byte[] hash, byte[] previousHash, int seqN, int consensusSeqN, List<ClientRequest> operations, int replicaId) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.seqN = seqN;
        this.consensusSeqN = consensusSeqN;
        this.operations = operations;
        this.replicaId = replicaId;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getPreviousHash() {
        return previousHash;
    }

    public int getSeqN() {
        return seqN;
    }

    public int getConsensusSeqN() {
        return consensusSeqN;
    }

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public int getReplicaId() {
        return replicaId;
    }

    private byte[] generateHash(){
        ByteBuffer buf = ByteBuffer.allocate((previousHash == null ? 0 : previousHash.length) + 3 * Integer.BYTES);
        if (previousHash != null)
            buf.put(previousHash);
        buf.putInt(seqN);
        buf.putInt(consensusSeqN);
        buf.putInt(replicaId);
        for (ClientRequest op : operations) {
            byte[] opBytes = op.toBytes();
            buf = ByteBuffer.allocate(opBytes.length);
            buf.put(opBytes);
        }
        return buf.array();
    }

    /*
     * Generates a signature for the block using the provided key. Owner of key should be that of the replica ID.
     */
    public void sign(PrivateKey key) {
        ByteBuf buf = Unpooled.buffer();

        try {
            Block.serializer.serialize(this, buf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.signature = SignaturesHelper.generateSignature(buf.array(), key);
    }

    public static ISerializer<Block> serializer = new ISerializer<>() {
        @Override
        public Block deserialize(ByteBuf in) throws IOException {
            byte[] hash = Utils.byteArraySerializer.deserialize(in);
            byte[] prevHash = Utils.byteArraySerializer.deserialize(in);
            int seqN = in.readInt();
            int consensusSeqN = in.readInt();
            int numOps = in.readInt();
            List<ClientRequest> ops = new ArrayList<>(numOps);
            for (int i = 0; i < numOps; i++) {
                ops.add(ClientRequest.serializer.deserialize(in));
            }
            int replicaId = in.readInt();
            return new Block(hash, prevHash, seqN, consensusSeqN, ops, replicaId);
        }

        @Override
        public void serialize(Block block, ByteBuf out) throws IOException {
            Utils.byteArraySerializer.serialize(block.hash, out);
            Utils.byteArraySerializer.serialize(block.previousHash, out);
            out.writeInt(block.seqN);
            out.writeInt(block.consensusSeqN);
            out.writeInt(block.operations.size());

            for (ClientRequest op : block.operations) {
                out.writeBytes(op.toBytes());
            }
            out.writeInt(block.replicaId);
        }
    };

}
