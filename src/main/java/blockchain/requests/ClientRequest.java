package blockchain.requests;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class ClientRequest extends ProtoRequest {

	public static final short REQUEST_ID = 201;
	
	private final UUID requestId;
	private final byte[] operation;
	
	public ClientRequest(byte[] operation) {
		super(ClientRequest.REQUEST_ID);
		this.requestId = UUID.randomUUID();
		this.operation = operation;
	}
	
	public ClientRequest(UUID id, byte[] operation) {
		super(ClientRequest.REQUEST_ID);
		this.requestId = id;
		this.operation = operation;
		
	}

	public UUID getRequestId() {
		return requestId;
	}
	
	public byte[] getOperation() {
		return operation;
	}
	
	static ClientRequest fromBytes(byte[] b) {
		ByteBuf bb = Unpooled.wrappedBuffer(b);
		UUID id = new UUID(bb.readLong(), bb.readLong());
		short s = bb.readShort();
		byte[] operation = new byte[s];
		bb.readBytes(operation);
		return new ClientRequest(id, operation);
	}
	
	public byte[] generateByteRepresentation() {
		ByteBuf bb = Unpooled.buffer(16 + Short.BYTES + operation.length);
		bb.writeLong(this.requestId.getMostSignificantBits());
		bb.writeLong(this.requestId.getLeastSignificantBits());
		bb.writeShort(this.operation.length);
		bb.writeBytes(this.operation);
		return ByteBufUtil.getBytes(bb);
	}
	
	
}
