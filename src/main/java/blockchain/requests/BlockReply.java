package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

//TODO
public class BlockReply extends ProtoReply {

    public static final short REPLY_ID = 201;

    private final int seqN;
    private final boolean wantHash;
    private final byte[] blockOrHash;

    public BlockReply(int seqN, boolean wantHash, byte[] blockOrHash) {
        super(REPLY_ID);

        this.seqN = seqN;
        this.wantHash = wantHash;
        this.blockOrHash = blockOrHash;
    }

    public int getSeqN() {
        return seqN;
    }

    public boolean getWantHash() {
    	return wantHash;
    }

    public byte[] getBlockOrHash() {
    	return blockOrHash;
    }
}
