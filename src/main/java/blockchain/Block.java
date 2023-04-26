package blockchain;

import java.util.List;
import java.util.UUID;

import blockchain.requests.ClientRequest;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Block  {


    private final String hash;
    private final String previousHash;
    private final int seqN;
    private final List<ClientRequest> operations;
    public UUID replicaId; // identity of the replica that generated the block and a signature.


    public Block(String hash, String previousHash, int seqN, List<ClientRequest> operations, UUID replicaId) {
        this.hash = hash;
        this.previousHash = previousHash;
        this.seqN = seqN;
        this.operations = operations;
        this.replicaId = replicaId;
    }

    public String getHash() {
        return hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public int getSeqN() {
        return seqN;
    }

    public List<ClientRequest> getOperations() {
        return operations;
    }

    public UUID getReplicaId() {
        return replicaId;
    }


    public static ISerializer<Block> serializer = new ISerializer<Block>() {
        public static byte[] toBytes(){
            return null;
        } 
    };



}
