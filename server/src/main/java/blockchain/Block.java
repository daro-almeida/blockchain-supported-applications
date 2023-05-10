package blockchain;

import blockchain.requests.ClientRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;
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
    private final int replicaId;// identity of the replica that generated the block and its signature.
    private final List<ClientRequest> operations;

    private byte[] signature;

    public Block(byte[] previousHash, List<ClientRequest> operations, int replicaId) {
        this.previousHash = previousHash;
        this.operations = operations;
        this.replicaId = replicaId;
        this.hash = Crypto.digest(blockContentsWithoutHash());
    }

    private Block(byte[] hash, byte[] prevHash, List<ClientRequest> ops, int replicaId) {
        this.hash = hash;
        this.previousHash = prevHash;
        this.operations = ops;
        this.replicaId = replicaId;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getSignature() {
        return signature;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public byte[] getPreviousHash() {
        return previousHash;
    }

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public byte[] blockContentsWithoutHash() {
        ByteBuffer buf = ByteBuffer.allocate((previousHash == null ? 0 : previousHash.length) + Integer.BYTES);

        if (previousHash != null)
            buf.put(previousHash);
        buf.putInt(replicaId);
        for (ClientRequest op : operations) {
            byte[] opBytes = op.toBytes();
            buf = ByteBuffer.allocate(opBytes.length);
            buf.put(opBytes);
        }
        return buf.array();
    }

    /*
     * Includes everything but signature.
     */
    public byte[] serialized() {
        var bytebuf = Unpooled.buffer();
        try {
            serializer.serialize(this, bytebuf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytebuf.array();
    }

    public static Block deserialize(byte[] block) {
        try {
            return serializer.deserialize(Unpooled.wrappedBuffer(block));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Generates a signature for the block using the provided key. Owner of key
     * should be that of the replica ID.
     */
    public byte[] sign(PrivateKey key) {
        this.signature = SignaturesHelper.generateSignature(serialized(), key);
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public static ISerializer<Block> serializer = new ISerializer<>() {

        @Override
        public void serialize(Block block, ByteBuf out) throws IOException {
            Utils.byteArraySerializer.serialize(block.hash, out);
            Utils.byteArraySerializer.serialize(block.previousHash, out);
            out.writeInt(block.replicaId);

            out.writeInt(block.operations.size());
            for (ClientRequest op : block.operations) {
                out.writeBytes(op.toBytes());
            }
        }

        @Override
        public Block deserialize(ByteBuf in) throws IOException {
            byte[] hash = Utils.byteArraySerializer.deserialize(in);
            byte[] prevHash = Utils.byteArraySerializer.deserialize(in);
            int replicaId = in.readInt();

            int numOps = in.readInt();
            List<ClientRequest> ops = new ArrayList<>(numOps);
            for (int i = 0; i < numOps; i++) {
                ops.add(ClientRequest.serializer.deserialize(in));
            }

            return new Block(hash, prevHash, ops, replicaId);
        }
    };
}
