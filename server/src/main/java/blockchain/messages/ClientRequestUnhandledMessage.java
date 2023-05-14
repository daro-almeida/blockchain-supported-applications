package blockchain.messages;

import blockchain.requests.ClientRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ClientRequestUnhandledMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 202;

	private final Set<ClientRequest> requests;
	private final int nodeId;

	public ClientRequestUnhandledMessage(Set<ClientRequest> requests, int nodeId) {
		super(ClientRequestUnhandledMessage.MESSAGE_ID);
		this.requests = requests;
		this.nodeId = nodeId;
	}

	public Set<ClientRequest> getRequests() {
		return requests;
	}

	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<ClientRequestUnhandledMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(ClientRequestUnhandledMessage protoMessage, ByteBuf out) throws IOException {
			out.writeInt(protoMessage.requests.size());
			for (ClientRequest request : protoMessage.requests) {
				ClientRequest.serializer.serialize(request, out);
			}
			out.writeInt(protoMessage.nodeId);

		}

		@Override
		public ClientRequestUnhandledMessage deserializeBody(ByteBuf in) throws IOException {
			int size = in.readInt();
			Set<ClientRequest> requests = new HashSet<>();
			for (int i = 0; i < size; i++) {
				var request = ClientRequest.serializer.deserialize(in);
				requests.add(request);
			}
			int nodeId = in.readInt();
			return new ClientRequestUnhandledMessage(requests, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
