package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

import java.util.Set;

public class HashRequest extends ProtoRequest {

    public static final short REQUEST_ID = 206;

    private final Set<Integer> hashesWanted;
    private final int requesterId;

    public HashRequest(Set<Integer> hashesWanted, int requesterId) {
        super(REQUEST_ID);

        this.hashesWanted = hashesWanted;
        this.requesterId = requesterId;
    }

    public Set<Integer> getHashesWanted() {
        return hashesWanted;
    }

    public int getRequesterId() {
    	return requesterId;
    }
}
