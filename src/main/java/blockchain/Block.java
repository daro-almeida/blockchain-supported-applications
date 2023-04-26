package blockchain;

import java.util.List;
import java.util.UUID;

import blockchain.requests.ClientRequest;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Block  {


    private final byte[] hash;
    private final byte[] previousHash;
    private final int seqN;
    private final List<ClientRequest> operations;
    public int replicaId; // identity of the replica that generated the block and a signature.


    public Block(byte[] hash, byte[] previousHash, int seqN, List<ClientRequest> operations, int replicaId) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.seqN = seqN;
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

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public int getReplicaId() {
        return replicaId;
    }


    public static ISerializer<Block> serializer = new ISerializer<Block>() {
        public static byte[] toBytes(){
            return null;
        } 
    };



}
