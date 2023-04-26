package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class BlockRequest extends ProtoRequest {

    public static final short REQUEST_ID = 202;

    private final int seqN;
    private final boolean wantHash;

    public BlockRequest(int seqN, boolean wantHash) {
        super(REQUEST_ID);

        this.seqN = seqN;
        this.wantHash = wantHash;
    }

    public int getSeqN() {
        return seqN;
    }

    public boolean getWantHash() {
    	return wantHash;
    }
}
