package app.messages.client.requests;

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

public class Cancel extends SignedProtoMessage {

	public final static short MESSAGE_ID = 305;
	
	private UUID rID;
	private PublicKey cID;
	
	public Cancel(UUID rID, PublicKey cID) {
		super(Cancel.MESSAGE_ID);
		this.rID = rID;
		this.cID = cID;
	}
	
	public final static SignedMessageSerializer<Cancel> serializer = new SignedMessageSerializer<Cancel>() {

		@Override
		public void serializeBody(Cancel c, ByteBuf out) throws IOException {
			out.writeLong(c.rID.getMostSignificantBits());
			out.writeLong(c.rID.getLeastSignificantBits());
			byte[] pk = c.cID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
		}

		@Override
		public Cancel deserializeBody(ByteBuf in) throws IOException {
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
			
			return new Cancel(new UUID(msb,lsb), cID);
		}
	};

	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return Cancel.serializer;
	}

	public UUID getrID() {
		return rID;
	}

	public void setrID(UUID rID) {
		this.rID = rID;
	}

	public PublicKey getcID() {
		return cID;
	}

	public void setcID(PublicKey cID) {
		this.cID = cID;
	}
	
}
