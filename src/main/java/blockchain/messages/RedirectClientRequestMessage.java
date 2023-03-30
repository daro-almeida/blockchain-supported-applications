package blockchain.messages;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

public class RedirectClientRequestMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 201;
	
	private final UUID requestId;
	private final byte[] operation;
	private final byte[] signature;
	private final int nodeId;

	public RedirectClientRequestMessage(UUID requestId, byte[] operation, int nodeId, byte[] signature) {
		super(RedirectClientRequestMessage.MESSAGE_ID);
		this.requestId = requestId;
		this.operation = operation;
		this.nodeId = nodeId;
		this.signature = signature;
	}

	public static final SignedMessageSerializer<RedirectClientRequestMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(RedirectClientRequestMessage protoMessage, ByteBuf out) throws IOException {
			Utils.uuidSerializer.serialize(protoMessage.requestId, out);
			Utils.byteArraySerializer.serialize(protoMessage.operation, out);
			Utils.byteArraySerializer.serialize(protoMessage.signature, out);
			out.writeInt(protoMessage.nodeId);
		}


		@Override
		public RedirectClientRequestMessage deserializeBody(ByteBuf in) throws IOException {
			UUID requestId = Utils.uuidSerializer.deserialize(in);
			byte[] operation = Utils.byteArraySerializer.deserialize(in);
			byte[] signature = Utils.byteArraySerializer.deserialize(in);
			int nodeId = in.readInt();
			return new RedirectClientRequestMessage(requestId, operation, nodeId, signature);

		}

	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}

}
