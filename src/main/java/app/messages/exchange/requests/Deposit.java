package app.messages.exchange.requests;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedMessageSerializer;
import pt.unl.fct.di.novasys.babel.generic.signed.SignedProtoMessage;

public class Deposit extends SignedProtoMessage{

	public final static short MESSAGE_ID = 301;
	
	private UUID rid;
	private PublicKey clientID;
	private float amount;
	
	public Deposit(PublicKey cID, float a) {
		super(Deposit.MESSAGE_ID);
		this.rid = UUID.randomUUID();
		this.clientID = cID;
		this.amount = a;	
	}

	public Deposit(UUID rid, PublicKey cID, float a) {
		super(Deposit.MESSAGE_ID);
		this.rid = rid;
		this.clientID = cID;
		this.amount = a;	
	}

	
	public final static SignedMessageSerializer<Deposit> serializer = new SignedMessageSerializer<Deposit>() {

		@Override
		public void serializeBody(Deposit d, ByteBuf out) throws IOException {
			out.writeLong(d.rid.getMostSignificantBits());
			out.writeLong(d.rid.getLeastSignificantBits());
			byte[] pk = d.clientID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
			out.writeFloat(d.amount);
		}

		@Override
		public Deposit deserializeBody(ByteBuf in) throws IOException {
			long msb = in.readLong();
			long lsb = in.readLong();
			short l = in.readShort();
			byte[] pk = new byte[l];
			in.readBytes(pk);
			PublicKey cID = null;
			try {
				cID = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pk));
			} catch (InvalidKeySpecException e) {
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			float a = in.readFloat();
			return new Deposit(new UUID(msb,lsb), cID, a);
		}
	
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return Deposit.serializer;
	}

	public float getAmount() {
		return amount;
	}

	public void setAmount(float amount) {
		this.amount = amount;
	}

	public PublicKey getClientID() {
		return clientID;
	}

	public void setClientID(PublicKey clientID) {
		this.clientID = clientID;
	}

	public UUID getRid() {
		return rid;
	}

	public void setRid(UUID rid) {
		this.rid = rid;
	}

}
