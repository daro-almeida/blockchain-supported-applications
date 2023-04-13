package app.messages.client.replies;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class CheckOperationStatus extends ProtoMessage {

	enum Status { UNKOWN, PENDING, EXECUTED, CANCELLED }
	
	public final static short MESSAGE_ID = 307;
	
	private UUID rID;
	private Status status;
	private String data;
	
	public CheckOperationStatus(UUID rID, Status status) {
		super(CheckOperationStatus.MESSAGE_ID);
		this.rID = rID;
		this.status = status;
		this.data = null;
	}

	public CheckOperationStatus(UUID rID, Status status, String data) {
		super(CheckOperationStatus.MESSAGE_ID);
		this.rID = rID;
		this.status = status;
		this.data = data;
	}
	
	public UUID getrID() {
		return rID;
	}

	public void setrID(UUID rID) {
		this.rID = rID;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public static ISerializer<CheckOperationStatus> serializer = new ISerializer<CheckOperationStatus>() {

		@Override
		public void serialize(CheckOperationStatus t, ByteBuf out) throws IOException {
			out.writeLong(t.rID.getMostSignificantBits());
			out.writeLong(t.rID.getLeastSignificantBits());
			out.writeShort(t.status.ordinal());
			if(t.data == null) {
				out.writeShort(0);
			} else {
				byte[] s = t.data.getBytes();
				out.writeShort(s.length);
				out.writeBytes(s);
			}
		}

		@Override
		public CheckOperationStatus deserialize(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			short s = in.readShort();
			short sl = in.readShort();
			if(sl == 0)
				return new CheckOperationStatus(new UUID(msb,lsb), Status.values()[s]);
			else {
				byte[] dd = new byte[sl];
				in.readBytes(dd);
				return new CheckOperationStatus(new UUID(msb,lsb), Status.values()[s], new String(dd));
			}
		}
	};

}
