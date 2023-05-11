package app.opengoods;

import java.security.PublicKey;
import java.util.UUID;

public class Offer implements Comparable<Offer> {

    private final UUID requestId;
    private final PublicKey clientId;
    private final String resourceType;
	private int stock;
	private final float pricePerUnit;
	
	public Offer(UUID requestId, PublicKey cID, String resourceType, int stock, float price) {
        this.requestId = requestId;
        this.clientId = cID;
		this.resourceType = resourceType;
		this.stock = stock;
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

    public int getStock() {
        return stock;
    }

    public void decreaseStock(int amount) {
        this.stock = stock - amount;
    }

    public float getPricePerUnit() {
        return pricePerUnit;
    }

    @Override
    public int compareTo(Offer o) {
        return Float.compare(this.pricePerUnit, o.pricePerUnit);
    }
}
