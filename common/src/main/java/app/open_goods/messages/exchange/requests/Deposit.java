package app.open_goods.messages.exchange.requests;

import app.WriteOperation;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

public class Deposit extends WriteOperation {

	public final static short MESSAGE_ID = 301;
	
	private final UUID rid;
	private final PublicKey clientID;
	private final float amount;
	
	public Deposit(PublicKey cID, float a) {
		super(Deposit.MESSAGE_ID);
		this.rid = UUID.randomUUID();
		this.clientID = cID;
		this.amount = a;	
	}

	private Deposit(UUID rid, PublicKey cID, float a) {
		super(Deposit.MESSAGE_ID);
		this.rid = rid;
		this.clientID = cID;
		this.amount = a;	
	}

	public final static ISerializer<Deposit> serializer = new ISerializer<>() {

		@Override
		public void serialize(Deposit d, ByteBuf out) throws IOException {
			out.writeLong(d.rid.getMostSignificantBits());
			out.writeLong(d.rid.getLeastSignificantBits());
			byte[] pk = d.clientID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
			out.writeFloat(d.amount);
		}

		@Override
		public Deposit deserialize(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			short l = in.readShort();
			byte[] pk = new byte[l];
			in.readBytes(pk);
			PublicKey cID = null;
			try {
				cID = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pk));
			} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			float a = in.readFloat();
			return new Deposit(new UUID(msb,lsb), cID, a);
		}
	
	};

	public float getAmount() {
		return amount;
	}

	public PublicKey getClientID() {
		return clientID;
	}


	public UUID getRid() {
		return rid;
	}

}
