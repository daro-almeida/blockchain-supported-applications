package app.open_goods.messages.client.requests;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

public class CheckOperationStatus extends ProtoMessage {
	
	public final static short MESSAGE_ID = 307;
	
	private final UUID rID;

	public CheckOperationStatus(UUID rID) {
		super(CheckOperationStatus.MESSAGE_ID);
		this.rID = rID;
	}
	
	public UUID getrID() {
		return rID;
	}

	public static ISerializer<CheckOperationStatus> serializer = new ISerializer<>() {

		@Override
		public void serialize(CheckOperationStatus t, ByteBuf out) {
			out.writeLong(t.rID.getMostSignificantBits());
			out.writeLong(t.rID.getLeastSignificantBits());
		}

		@Override
		public CheckOperationStatus deserialize(ByteBuf in) {
			long msb = in.readLong();
			long lsb = in.readLong();

			return new CheckOperationStatus(new UUID(msb, lsb));

		}
	};

}
