package blockchain.requests;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;
import utils.SignaturesHelper;
import utils.Utils;

public class ClientRequest extends ProtoRequest {

	public static final short REQUEST_ID = 201;

	private final UUID requestId;
	private final byte[] operation;
	private final PublicKey publicKey;
	private final byte[] signature;

	public ClientRequest(byte[] operation, PublicKey publicKey, byte[] signature) {
		this(UUID.randomUUID(), operation, publicKey, signature);
	}

	public ClientRequest(byte[] operation, PublicKey publicKey, PrivateKey privateKey) {
		super(ClientRequest.REQUEST_ID);

		this.requestId = UUID.randomUUID();
		this.operation = operation;
		this.publicKey = publicKey;

		ByteBuffer buf = ByteBuffer.allocate(16 + operation.length + publicKey.getEncoded().length);
		buf.putLong(requestId.getMostSignificantBits());
		buf.putLong(requestId.getLeastSignificantBits());
		buf.put(operation);
		buf.put(publicKey.getEncoded());

		this.signature = SignaturesHelper.generateSignature(buf.array(), privateKey);
	}

	private ClientRequest(UUID id, byte[] operation, PublicKey publicKey, byte[] signature) {
		super(ClientRequest.REQUEST_ID);
		this.requestId = id;
		this.operation = operation;
		this.publicKey = publicKey;
		this.signature = signature;
	}
	public UUID getRequestId() {
		return requestId;
	}

	public byte[] getOperation() {
		return operation;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public boolean checkSignature() {
		ByteBuffer buf = ByteBuffer.allocate(16 + operation.length + publicKey.getEncoded().length);
		buf.putLong(requestId.getMostSignificantBits());
		buf.putLong(requestId.getLeastSignificantBits());
		buf.put(operation);
		buf.put(publicKey.getEncoded());

		return SignaturesHelper.checkSignature(buf.array(), signature, publicKey);
	}

	public byte[] toBytes() {
		ByteBuf buf = Unpooled.buffer();
		try {
			serializer.serialize(this, buf);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return ByteBufUtil.getBytes(buf);
	}

	public static final ISerializer<ClientRequest> serializer = new ISerializer<>() {
		@Override
		public void serialize(ClientRequest request, ByteBuf out) throws IOException {
			Utils.uuidSerializer.serialize(request.requestId, out);
			Utils.byteArraySerializer.serialize(request.operation, out);
			Utils.byteArraySerializer.serialize(request.publicKey.getEncoded(), out);
			Utils.byteArraySerializer.serialize(request.signature, out);
		}

		@Override
		public ClientRequest deserialize(ByteBuf in) throws IOException {
			UUID requestId = Utils.uuidSerializer.deserialize(in);
			byte[] operation = Utils.byteArraySerializer.deserialize(in);
			byte[] publicKeyBytes = Utils.byteArraySerializer.deserialize(in);
			byte[] signature = Utils.byteArraySerializer.deserialize(in);
			var publicKey = Crypto.getPublicKeyFromBytes(publicKeyBytes);
			return new ClientRequest(requestId, operation, publicKey, signature);
		}
	};
}
