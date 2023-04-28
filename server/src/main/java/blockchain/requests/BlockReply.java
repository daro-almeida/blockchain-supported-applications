package blockchain.requests;

import consensus.requests.ProposeRequest;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

import java.util.Map;

public class BlockReply extends ProtoReply {

    public static final short REPLY_ID = 201;

    private final Map<Integer, ProposeRequest> blocksWithSignatures;
    private final int requesterId;

    public BlockReply(Map<Integer, ProposeRequest> blocksWithSignatures, int requesterId) {
        super(REPLY_ID);

        this.blocksWithSignatures = blocksWithSignatures;
        this.requesterId = requesterId;
    }

    public Map<Integer, ProposeRequest> getBlocksWithSignatures() {
        return blocksWithSignatures;
    }

    public int getRequesterId() {
    	return requesterId;
    }
}
