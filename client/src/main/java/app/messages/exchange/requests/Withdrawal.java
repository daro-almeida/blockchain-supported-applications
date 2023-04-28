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

public class Withdrawal extends SignedProtoMessage{

	public final static short MESSAGE_ID = 302;
	
	private UUID rid;
	private PublicKey clientID;
	private float amount;
	
	public Withdrawal(PublicKey cID, float a) {
		super(Withdrawal.MESSAGE_ID);
		this.rid = UUID.randomUUID();
		this.clientID = cID;
		this.amount = a;	
	}

	public Withdrawal(UUID rid, PublicKey cID, float a) {
		super(Withdrawal.MESSAGE_ID);
		this.rid = rid;
		this.clientID = cID;
		this.amount = a;	
	}

	
	public final static SignedMessageSerializer<Withdrawal> serializer = new SignedMessageSerializer<Withdrawal>() {

		@Override
		public void serializeBody(Withdrawal w, ByteBuf out) throws IOException {
			out.writeLong(w.rid.getMostSignificantBits());
			out.writeLong(w.rid.getLeastSignificantBits());
			byte[] pk = w.clientID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
			out.writeFloat(w.amount);
		}

		@Override
		public Withdrawal deserializeBody(ByteBuf in) throws IOException {
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
			return new Withdrawal(new UUID(msb,lsb), cID, a);
		}
	
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return Withdrawal.serializer;
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
