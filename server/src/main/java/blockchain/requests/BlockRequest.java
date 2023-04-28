package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

import java.util.Set;

public class BlockRequest extends ProtoRequest {

    public static final short REQUEST_ID = 202;

    private final Set<Integer> blocksWanted;
    private final int requesterId;

    public BlockRequest(Set<Integer> blocksWanted, int requesterId) {
        super(REQUEST_ID);

        this.blocksWanted = blocksWanted;
        this.requesterId = requesterId;
    }

    public Set<Integer> getBlocksWanted() {
        return blocksWanted;
    }

    public int getRequesterId() {
    	return requesterId;
    }
}
