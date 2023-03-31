package blockchain.messages;

import java.io.IOException;

import blockchain.requests.ClientRequest;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

//TODO implement this message
public class ClientRequestUnhandledMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 202;

	private final ClientRequest request;
	private final byte[] signature; // this is the request's signature signed by the client
	private final int nodeId;

	public ClientRequestUnhandledMessage(ClientRequest request, byte[] signature, int nodeId) {
		super(ClientRequestUnhandledMessage.MESSAGE_ID);
		this.request = request;
		this.signature = signature;
		this.nodeId = nodeId;
	}

	public ClientRequest getRequest() {
		return request;
	}

	public byte[] getSignature() {
		return signature;
	}

	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<ClientRequestUnhandledMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(ClientRequestUnhandledMessage protoMessage, ByteBuf out) throws IOException {
			Utils.byteArraySerializer.serialize(protoMessage.request.generateByteRepresentation(), out);
			Utils.byteArraySerializer.serialize(protoMessage.signature, out);
			out.writeInt(protoMessage.nodeId);

		}

		@Override
		public ClientRequestUnhandledMessage deserializeBody(ByteBuf in) throws IOException {
			byte[] requestBytes = Utils.byteArraySerializer.deserialize(in);
			byte[] signature = Utils.byteArraySerializer.deserialize(in);
			var request = ClientRequest.fromBytes(requestBytes);
			int nodeId = in.readInt();
			return new ClientRequestUnhandledMessage(request, signature, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
