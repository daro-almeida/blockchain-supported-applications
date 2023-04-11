package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

//TODO
public class BlockReply extends ProtoReply {

    public static final short REPLY_ID = 201;

    public BlockReply() {
        super(REPLY_ID);
    }
}
