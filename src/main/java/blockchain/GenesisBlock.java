package blockchain;

import java.util.List;

import blockchain.requests.ClientRequest;

public class GenesisBlock extends Block {

    public GenesisBlock(byte[] previousHash, int seqN, List<ClientRequest> operations, int replicaId) {
        super(null, 0, operations, replicaId);
        //TODO Auto-generated constructor stub
    }
}
