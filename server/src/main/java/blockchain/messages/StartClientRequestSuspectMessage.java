package blockchain.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StartClientRequestSuspectMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 203;

	private final Set<UUID> requestIds;
	private final int nodeId;

	public StartClientRequestSuspectMessage(Set<UUID> requestIds, int nodeId) {
		super(StartClientRequestSuspectMessage.MESSAGE_ID);
		this.requestIds = requestIds;
		this.nodeId = nodeId;
	}

	public Set<UUID> getRequestIds() {
		return requestIds;
	}

	public int getNodeId() {
		return nodeId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		StartClientRequestSuspectMessage that = (StartClientRequestSuspectMessage) o;
		return getNodeId() == that.getNodeId() && Objects.equals(getRequestIds(), that.getRequestIds());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRequestIds(), getNodeId());
	}

	public static final SignedMessageSerializer<StartClientRequestSuspectMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(StartClientRequestSuspectMessage protoMessage, ByteBuf out) throws IOException {
			out.writeInt(protoMessage.requestIds.size());
			for (UUID requestId : protoMessage.requestIds) {
				Utils.uuidSerializer.serialize(requestId, out);
			}
			out.writeInt(protoMessage.nodeId);
		}

		@Override
		public StartClientRequestSuspectMessage deserializeBody(ByteBuf in) throws IOException {
			int size = in.readInt();
			Set<UUID> requestIds = new HashSet<>(size);
			for (int i = 0; i < size; i++) {
				UUID requestId = Utils.uuidSerializer.deserialize(in);
				requestIds.add(requestId);
			}
			int nodeId = in.readInt();
			return new StartClientRequestSuspectMessage(requestIds, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
