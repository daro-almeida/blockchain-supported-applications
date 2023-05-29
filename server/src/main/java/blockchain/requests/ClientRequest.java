package blockchain.requests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.ISerializer;
import utils.Crypto;
import utils.SignaturesHelper;
import utils.Utils;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.UUID;

public class ClientRequest extends ProtoRequest {

	public static final short REQUEST_ID = 203;

	private final UUID requestId;
	private final byte[] operation;
	private final PublicKey publicKey;
	private final byte[] signature;

	public ClientRequest(UUID id, byte[] operation, byte[] signature, PublicKey publicKey) {
		super(ClientRequest.REQUEST_ID);
		this.requestId = id;
		this.operation = operation;
		this.signature = signature;
		this.publicKey = publicKey;
	}

	public ClientRequest(byte[] operation, PublicKey publicKey, PrivateKey privateKey) {
		super(ClientRequest.REQUEST_ID);

		this.requestId = UUID.randomUUID();
		this.operation = operation;
		this.publicKey = publicKey;
		this.signature = SignaturesHelper.generateSignature(operation, privateKey);
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
		return SignaturesHelper.checkSignature(operation, signature, publicKey);
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ClientRequest that = (ClientRequest) o;
		return Objects.equals(requestId, that.requestId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId);
	}

	@Override
	public String toString() {
		return requestId.toString();
	}

	public static final ISerializer<ClientRequest> serializer = new ISerializer<>() {
		@Override
		public void serialize(ClientRequest request, ByteBuf out) throws IOException {
			Utils.uuidSerializer.serialize(request.requestId, out);
			Utils.byteArraySerializer.serialize(request.operation, out);
			Utils.rsaPublicKeySerializer.serialize(request.publicKey, out);
			Utils.byteArraySerializer.serialize(request.signature, out);
		}

		@Override
		public ClientRequest deserialize(ByteBuf in) throws IOException {
			UUID requestId = Utils.uuidSerializer.deserialize(in);
			byte[] operation = Utils.byteArraySerializer.deserialize(in);
			var publicKey = Utils.rsaPublicKeySerializer.deserialize(in);
			byte[] signature = Utils.byteArraySerializer.deserialize(in);
			return new ClientRequest(requestId, operation, signature, publicKey);
		}
	};
}
