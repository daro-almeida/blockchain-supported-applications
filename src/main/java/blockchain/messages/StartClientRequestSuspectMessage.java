package blockchain.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;
import utils.Utils;

import java.io.IOException;

//TODO implement this message
public class StartClientRequestSuspectMessage extends SignedProtoMessage {

	public final static short MESSAGE_ID = 203;

	private final short requestId; 
	private final byte[] signature; // this is the request's signature signed by the client
	private final int nodeId;

	public StartClientRequestSuspectMessage(short requestId, byte[] signature, int nodeId) {
		super(StartClientRequestSuspectMessage.MESSAGE_ID);
		this.requestId = requestId;
		this.signature = signature;
		this.nodeId = nodeId;
	}

	public short getRequestId() {
		return requestId;
	}

	public byte[] getSignature() {
		return signature;
	}

	public int getNodeId() {
		return nodeId;
	}

	public static final SignedMessageSerializer<StartClientRequestSuspectMessage> serializer = new SignedMessageSerializer<>() {

		@Override
		public void serializeBody(StartClientRequestSuspectMessage protoMessage, ByteBuf out) throws IOException {
			out.writeShort(protoMessage.requestId);
			Utils.byteArraySerializer.serialize(protoMessage.signature, out);
			out.writeInt(protoMessage.nodeId);

		}

		@Override
		public StartClientRequestSuspectMessage deserializeBody(ByteBuf in) throws IOException {
			short id = in.readShort();
			byte[] signature = Utils.byteArraySerializer.deserialize(in);
			int nodeId = in.readInt();
			return new StartClientRequestSuspectMessage(id, signature, nodeId);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return serializer;
	}
}
