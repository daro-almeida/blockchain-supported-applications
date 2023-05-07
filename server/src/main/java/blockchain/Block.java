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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class Block implements Iterable<ClientRequest> {

    private final byte[] hash;
    private final byte[] previousHash;

    private final int replicaId;// identity of the replica that generated the block and its signature.
    // checkar ops repetidas
    // checkar se assinatura das ops sao validas
    private final List<ClientRequest> operations;

    private byte[] signature;

    public Block(byte[] previousHash, List<ClientRequest> operations, int replicaId) {
        this.previousHash = previousHash;
        this.operations = operations;
        this.replicaId = replicaId;
        this.hash = Crypto.digest(blockContentsWithoutHash());
    }

    private Block(byte[] hash, byte[] prevHash, List<ClientRequest> ops, int replicaId, byte[] signature) {
        this.hash = hash;
        this.previousHash = prevHash;
        this.operations = ops;
        this.replicaId = replicaId;
        this.signature = signature;

    }

    public boolean equals(Block other) {
        return (hash == null && other.hash == null) || hash.equals(other.hash);
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

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public byte[] getPreviousHash() {
        return previousHash;
    }

    public static ISerializer<Block> getSerializer() {
        return serializer;
    }

    /*
     * Includes everything but signature.
     */
    public byte[] blockContents() {
        ByteBuffer buf = ByteBuffer.allocate(hash.length);
        buf.put(hash);
        var rest = blockContentsWithoutHash();
        buf = ByteBuffer.allocate(rest.length);
        buf.put(rest);
        return buf.array();
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

    public static Block fromBytes(byte[] block) {
        try {
            return serializer.deserialize(Unpooled.wrappedBuffer(block));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /*
     * Generates a signature for the block using the provided key. Owner of key
     * should be that of the replica ID.
     */
    public void sign(PrivateKey key) {
        this.signature = SignaturesHelper.generateSignature(blockContents(), key);
    }

    /*
     * Verifies the signature of the block using the provided public key. Public key
     * should be that of the replica ID.
     */
    public boolean checkSignature(PublicKey publicKey) {
        return SignaturesHelper.checkSignature(blockContents(), signature, publicKey);
    }

    public static ISerializer<Block> serializer = new ISerializer<>() {

        @Override
        public void serialize(Block block, ByteBuf out) throws IOException {
            if (block.signature == null)
                throw new RuntimeException("Block not signed");
            Utils.byteArraySerializer.serialize(block.hash, out);
            Utils.byteArraySerializer.serialize(block.previousHash, out);
            out.writeInt(block.replicaId);

            out.writeInt(block.operations.size());
            for (ClientRequest op : block.operations) {
                out.writeBytes(op.toBytes());
            }

            Utils.byteArraySerializer.serialize(block.signature, out);
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

            byte[] signature = Utils.byteArraySerializer.deserialize(in);

            return new Block(hash, prevHash, ops, replicaId, signature);
        }
    };

    @Override
    public Iterator<ClientRequest> iterator() {
        return operations.iterator();
    }

    @Override
    public void forEach(Consumer<? super ClientRequest> action) {
        operations.forEach(action);
    }
}
