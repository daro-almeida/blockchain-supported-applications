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

public class Withdrawal extends WriteOperation {

	public final static short MESSAGE_ID = 302;
	
	private final UUID rid;
	private final PublicKey clientID;
	private final float amount;
	
	public Withdrawal(PublicKey cID, float a) {
		super(Withdrawal.MESSAGE_ID);
		this.rid = UUID.randomUUID();
		this.clientID = cID;
		this.amount = a;	
	}

	private Withdrawal(UUID rid, PublicKey cID, float a) {
		super(Withdrawal.MESSAGE_ID);
		this.rid = rid;
		this.clientID = cID;
		this.amount = a;	
	}

	public final static ISerializer<Withdrawal> serializer = new ISerializer<>() {

		@Override
		public void serialize(Withdrawal w, ByteBuf out) {
			out.writeLong(w.rid.getMostSignificantBits());
			out.writeLong(w.rid.getLeastSignificantBits());
			byte[] pk = w.clientID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
			out.writeFloat(w.amount);
		}

		@Override
		public Withdrawal deserialize(ByteBuf in) throws IOException {
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
			return new Withdrawal(new UUID(msb,lsb), cID, a);
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
