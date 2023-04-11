package blockchain.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

//TODO
public class BlockRequest extends ProtoRequest {

    public static final short REQUEST_ID = 202;
    public BlockRequest() {
        super(REQUEST_ID);
    }
}
