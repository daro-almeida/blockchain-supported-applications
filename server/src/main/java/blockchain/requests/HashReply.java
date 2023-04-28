package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

import java.util.Map;

public class HashReply extends ProtoReply {

    public static final short REPLY_ID = 202;

    private final Map<Integer, byte[]> hashes;
    private final int requesterId;

    public HashReply(Map<Integer, byte[]> hashes, int requesterId) {
        super(REPLY_ID);

        this.hashes = hashes;
        this.requesterId = requesterId;
    }

    public Map<Integer, byte[]> getHashes() {
        return hashes;
    }

    public int getRequesterId() {
    	return requesterId;
    }
}
