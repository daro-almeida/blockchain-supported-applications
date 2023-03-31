package blockchain.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.UUID;

public class StartClientRequestSuspectMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 203;

	private final UUID requestId;
	private final int nodeId;

	public StartClientRequestSuspectMessage(UUID requestId, int nodeId) {
		super(StartClientRequestSuspectMessage.MESSAGE_ID);
		this.requestId = requestId;
		this.nodeId = nodeId;
	}

	public UUID getRequestId() {
		return requestId;
	}

	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<StartClientRequestSuspectMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(StartClientRequestSuspectMessage protoMessage, ByteBuf out) throws IOException {
			Utils.uuidSerializer.serialize(protoMessage.requestId, out);
			out.writeInt(protoMessage.nodeId);
		}

		@Override
		public StartClientRequestSuspectMessage deserializeBody(ByteBuf in) throws IOException {
			UUID id = Utils.uuidSerializer.deserialize(in);
			int nodeId = in.readInt();
			return new StartClientRequestSuspectMessage(id, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
