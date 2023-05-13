package app.open_goods;

import java.security.PublicKey;
import java.util.UUID;

public class Want implements Comparable<Want> {

    private final UUID requestId;
    private final PublicKey clientId;
    private final String resourceType;
	private int quantity;
	private final float pricePerUnit;

	public Want(UUID requestId, PublicKey cID, String resourceType, int quantity, float price) {
        this.requestId = requestId;
        this.clientId = cID;
        this.resourceType = resourceType;
		this.quantity = quantity;
        this.pricePerUnit = price;
	}

    public UUID getRequestId() {
        return requestId;
    }

    public PublicKey getClientId() {
        return clientId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void decreaseQuantity(int amount) {
        this.quantity = quantity - amount;
    }

    public float getPricePerUnit() {
        return pricePerUnit;
    }


    @Override
    public int compareTo(Want o) {
        return Float.compare(this.pricePerUnit, o.pricePerUnit);
    }
}
