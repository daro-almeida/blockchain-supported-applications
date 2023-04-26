package blockchain;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import blockchain.requests.ClientRequest;
import client.Client;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;
import utils.Utils;

public class Block {

    private final byte[] hash;
    private final byte[] previousHash;
    private final int seqN;
    private final int replicaId; // identity of the replica that generated the block and a signature.
    private final List<ClientRequest> operations;

    public Block(byte[] previousHash, int seqN, List<ClientRequest> operations, int replicaId) {
        this.previousHash = previousHash;
        this.seqN = seqN;
        this.operations = operations;
        this.replicaId = replicaId;
        this.hash = generateHash();
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

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public int getReplicaId() {
        return replicaId;
    }

    private byte[] generateHash(){
        ByteBuffer buf = ByteBuffer.allocate(previousHash.length+Integer.BYTES+Integer.BYTES);
        buf.put(previousHash);
        buf.putInt(seqN);
        buf.putInt(replicaId);
        for (ClientRequest op : operations) {
            byte[] opBytes = op.generateByteRepresentation();
            buf = ByteBuffer.allocate(opBytes.length);
            buf.put(opBytes);
        }
        return buf.array();
    }

    public static ISerializer<Block> serializer = new ISerializer<Block>() {
        @Override
        public Block deserialize(ByteBuf in) throws IOException {
            byte[] hash = Utils.byteArraySerializer.deserialize(in);
            byte[] prevHash = Utils.byteArraySerializer.deserialize(in);
            int seqN = in.readInt();
            int numOps = in.readInt();
            List<ClientRequest> ops = new ArrayList<>(numOps);
            for (int i = 0; i < numOps; i++) {
                ops.add(ClientRequest.serializer.deserialize(in));
            }
            int replicaId = in.readInt();
            return new Block(prevHash, seqN, ops, replicaId);
        }

        @Override
        public void serialize(Block block, ByteBuf out) throws IOException {
            Utils.byteArraySerializer.serialize(block.hash, out);
            Utils.byteArraySerializer.serialize(block.previousHash, out);
            out.writeInt(block.seqN);
            out.writeInt(block.operations.size());

            for (ClientRequest op : block.operations) {
                out.writeBytes(op.generateByteRepresentation());
            }
            out.writeInt(block.replicaId);
        }
    };

}
