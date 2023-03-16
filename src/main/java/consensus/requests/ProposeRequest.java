package consensus.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class ProposeRequest extends ProtoRequest {

	public static final short REQUEST_ID = 101;
	
	private final byte[] block;
	private final byte[] signature;
	
	public ProposeRequest(byte[] block, byte[] signature) {
		super(ProposeRequest.REQUEST_ID);
		this.block = block;
		this.signature = signature;
	}

	public byte[] getBlock() {
		return block;
	}

	public byte[] getSignature() {
		return signature;
	}
	
	

}
