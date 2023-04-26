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

public class IssueOffer extends SignedProtoMessage {

	public final static short MESSAGE_ID = 303;
	
	private UUID rid;
	private PublicKey cID;
	private String resourceType;
	private int quantity;
	private float pricePerUnit;
	
	
	public IssueOffer(PublicKey cID, String resourceType, int quantity, float price) {
		super(IssueOffer.MESSAGE_ID);
		this.setRid(UUID.randomUUID());
		this.setcID(cID);
		this.setResourceType(resourceType);
		this.setQuantity(quantity);
		this.setPricePerUnit(price);
		
	}

	public IssueOffer(UUID rid, PublicKey cID, String resourceType, int quantity, float price) {
		super(IssueOffer.MESSAGE_ID);
		this.setRid(rid);
		this.setcID(cID);
		this.setResourceType(resourceType);
		this.setQuantity(quantity);
		this.setPricePerUnit(price);
		
	}
	
	public static final SignedMessageSerializer<IssueOffer> serializer = new SignedMessageSerializer<IssueOffer>() {

		@Override
		public void serializeBody(IssueOffer io, ByteBuf out) throws IOException {
			out.writeLong(io.rid.getMostSignificantBits());
			out.writeLong(io.rid.getLeastSignificantBits());
			byte[] pk = io.cID.getEncoded();
			out.writeShort(pk.length);
			out.writeBytes(pk);
			byte[] r = io.resourceType.getBytes();
			out.writeShort(r.length);
			out.writeBytes(r);
			out.writeInt(io.quantity);
			out.writeFloat(io.pricePerUnit);
		}

		@Override
		public IssueOffer deserializeBody(ByteBuf in) throws IOException {
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
			l = in.readShort();
			byte[] rt = new byte[l];
			in.readBytes(rt);
			int q = in.readInt();
			float pu = in.readFloat();
			return new IssueOffer(new UUID(msb,lsb), cID, new String(rt), q, pu);
			
		}
	
	};
	
	@Override
	public SignedMessageSerializer<? extends SignedProtoMessage> getSerializer() {
		return IssueOffer.serializer;
	}

	public UUID getRid() {
		return rid;
	}

	public void setRid(UUID rid) {
		this.rid = rid;
	}

	public PublicKey getcID() {
		return cID;
	}

	public void setcID(PublicKey cID) {
		this.cID = cID;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public float getPricePerUnit() {
		return pricePerUnit;
	}

	public void setPricePerUnit(float pricePerUnit) {
		this.pricePerUnit = pricePerUnit;
	}

}
