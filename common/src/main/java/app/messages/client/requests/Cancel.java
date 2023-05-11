package app.messages.client.requests;

import app.messages.WriteOperation;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

public class Cancel extends WriteOperation {

	public final static short MESSAGE_ID = 305;
	
	private final UUID rID;
	private final UUID targetRequest;
	private final PublicKey cID;
	
	public Cancel(UUID targetRequest, PublicKey cID) {
		super(Cancel.MESSAGE_ID, OperationType.CANCEL);
		this.rID = UUID.randomUUID();
		this.targetRequest = targetRequest;
		this.cID = cID;
	}

	private Cancel(UUID rID, UUID targetRequest, PublicKey cID) {
		super(Cancel.MESSAGE_ID, OperationType.CANCEL);
		this.rID = rID;
		this.targetRequest = targetRequest;
		this.cID = cID;
	}
	
	public final static ISerializer<Cancel> serializer = new ISerializer<>() {

		@Override
		public void serialize(Cancel c, ByteBuf out) throws IOException {
			out.writeLong(c.rID.getMostSignificantBits());
			out.writeLong(c.rID.getLeastSignificantBits());
			out.writeLong(c.targetRequest.getMostSignificantBits());
			out.writeLong(c.targetRequest.getLeastSignificantBits());
			byte[] pk = c.cID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
		}

		@Override
		public Cancel deserialize(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			long msb2 = in.readLong();
			long lsb2 = in.readLong();
			short l = in.readShort();
			byte[] pk = new byte[l];
			in.readBytes(pk);
			PublicKey cID = null;
			try {
				cID = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pk));
			} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
				e.printStackTrace();
			}

			return new Cancel(new UUID(msb,lsb), new UUID(msb2, lsb2), cID);
		}
	};

	public UUID getrID() {
		return rID;
	}

	public UUID getTargetRequest() {
		return targetRequest;
	}

	public PublicKey getcID() {
		return cID;
	}
}
