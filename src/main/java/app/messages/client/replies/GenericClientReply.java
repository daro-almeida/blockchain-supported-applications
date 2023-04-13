package app.messages.client.replies;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class GenericClientReply extends ProtoMessage {

	public final static short MESSAGE_ID = 306;
	
	private UUID rID;
	
	public GenericClientReply(UUID rID) {
		super(GenericClientReply.MESSAGE_ID);
		this.rID = rID;
	}

	public UUID getrID() {
		return rID;
	}

	public void setrID(UUID rID) {
		this.rID = rID;
	}

	public static ISerializer<GenericClientReply> serializer = new ISerializer<GenericClientReply>() {

		@Override
		public void serialize(GenericClientReply t, ByteBuf out) throws IOException {
			out.writeLong(t.rID.getMostSignificantBits());
			out.writeLong(t.rID.getLeastSignificantBits());
		}

		@Override
		public GenericClientReply deserialize(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			return new GenericClientReply(new UUID(msb,lsb));
		}
	};

}
