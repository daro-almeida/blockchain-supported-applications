package app.open_goods.messages.client.requests;

import app.WriteOperation;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.UUID;

public class IssueOffer extends WriteOperation {

	public final static short MESSAGE_ID = 303;
	
	private final UUID rid;
	private final PublicKey cID;
	private final String resourceType;
	private final int quantity;
	private final float pricePerUnit;
	
	
	public IssueOffer(PublicKey cID, String resourceType, int quantity, float price) {
		super(IssueOffer.MESSAGE_ID);
		this.rid = UUID.randomUUID();
		this.cID = cID;
		this.resourceType = resourceType;
		this.quantity = quantity;
		this.pricePerUnit = price;
	}

	private IssueOffer(UUID rid, PublicKey cID, String resourceType, int quantity, float price) {
		super(IssueOffer.MESSAGE_ID);
		this.rid = rid;
		this.cID = cID;
		this.resourceType = resourceType;
		this.quantity = quantity;
		this.pricePerUnit = price;
	}
	
	public static final ISerializer<IssueOffer> serializer = new ISerializer<>() {

		@Override
		public void serialize(IssueOffer io, ByteBuf out) throws IOException {
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
		public IssueOffer deserialize(ByteBuf in) throws IOException {
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
			l = in.readShort();
			byte[] rt = new byte[l];
			in.readBytes(rt);
			int q = in.readInt();
			float pu = in.readFloat();
			return new IssueOffer(new UUID(msb,lsb), cID, new String(rt), q, pu);
			
		}
	
	};

	public UUID getRid() {
		return rid;
	}

	public PublicKey getcID() {
		return cID;
	}

	public String getResourceType() {
		return resourceType;
	}

	public int getQuantity() {
		return quantity;
	}

	public float getPricePerUnit() {
		return pricePerUnit;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IssueOffer that = (IssueOffer) o;
		return Objects.equals(rid, that.rid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rid);
	}
}
