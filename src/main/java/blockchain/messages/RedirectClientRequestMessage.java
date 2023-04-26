package blockchain.messages;

import blockchain.requests.ClientRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;

public class RedirectClientRequestMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 201;
	
	private final ClientRequest request;
	private final int nodeId;

	public RedirectClientRequestMessage(ClientRequest request, int nodeId) {
		super(RedirectClientRequestMessage.MESSAGE_ID);
		this.request = request;
		this.nodeId = nodeId;
	}

	public ClientRequest getRequest() {
		return request;
	}
	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<RedirectClientRequestMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(RedirectClientRequestMessage protoMessage, ByteBuf out) throws IOException {
			ClientRequest.serializer.serialize(protoMessage.request, out);
			out.writeInt(protoMessage.nodeId);
		}

		@Override
		public RedirectClientRequestMessage deserializeBody(ByteBuf in) throws IOException {
			var request = ClientRequest.serializer.deserialize(in);
			int nodeId = in.readInt();
			return new RedirectClientRequestMessage(request, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}

}
