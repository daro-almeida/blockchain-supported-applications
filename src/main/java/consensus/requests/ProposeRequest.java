package consensus.requests;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;

import java.io.IOException;

public class ProposeRequest extends ProtoRequest {

	public static final short REQUEST_ID = 101;
	
	private final byte[] block;
	private final byte[] signature;
	private byte[] digest;
	
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

	public byte[] getDigest() {
		if (digest == null) {
			// create byte array with block and signature
			byte[] blockAndSignature = new byte[block.length + signature.length];
			System.arraycopy(block, 0, blockAndSignature, 0, block.length);
			System.arraycopy(signature, 0, blockAndSignature, block.length, signature.length);

			digest = Crypto.digest(blockAndSignature);
		}
		return digest;
	}
	
	public static final ISerializer<ProposeRequest> serializer = new ISerializer<>() {
		@Override
		public void serialize(ProposeRequest proposeRequest, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(proposeRequest.block.length);
			byteBuf.writeBytes(proposeRequest.block);
			byteBuf.writeInt(proposeRequest.signature.length);
			byteBuf.writeBytes(proposeRequest.signature);
		}

		@Override
		public ProposeRequest deserialize(ByteBuf byteBuf) throws IOException {
			byte[] block = new byte[byteBuf.readInt()];
			byteBuf.readBytes(block);
			byte[] signature = new byte[byteBuf.readInt()];
			byteBuf.readBytes(signature);

			return new ProposeRequest(block, signature);
		}
	};
}
