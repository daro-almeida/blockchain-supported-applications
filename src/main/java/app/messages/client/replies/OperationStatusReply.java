package app.messages.client.replies;

import java.io.IOException;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class OperationStatusReply extends ProtoMessage {

	public enum Status { UNKOWN, PENDING, EXECUTED, CANCELLED, REJECTED, FAILED }
	
	public final static short MESSAGE_ID = 307;
	
	private UUID rID;
	private Status status;
	private String data;
	
	public OperationStatusReply(UUID rID, Status status) {
		super(OperationStatusReply.MESSAGE_ID);
		this.rID = rID;
		this.status = status;
		this.data = null;
	}

	public OperationStatusReply(UUID rID, Status status, String data) {
		super(OperationStatusReply.MESSAGE_ID);
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

	public static ISerializer<OperationStatusReply> serializer = new ISerializer<OperationStatusReply>() {

		@Override
		public void serialize(OperationStatusReply t, ByteBuf out) throws IOException {
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
		public OperationStatusReply deserialize(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			short s = in.readShort();
			short sl = in.readShort();
			if(sl == 0)
				return new OperationStatusReply(new UUID(msb,lsb), Status.values()[s]);
			else {
				byte[] dd = new byte[sl];
				in.readBytes(dd);
				return new OperationStatusReply(new UUID(msb,lsb), Status.values()[s], new String(dd));
			}
		}
	};

}
