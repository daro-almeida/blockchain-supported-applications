package utils;

import java.security.PublicKey;

public class Want {

    private final PublicKey clientId;
    private final String resourceType;
	private int quantity;
	private final float pricePerUnit;
	
	
	public Want(PublicKey cID, String resourceType, int quantity, float price) {
        this.clientId = cID;
        this.resourceType = resourceType;
		this.decreaseQuantity(quantity);
        this.pricePerUnit = price;
		
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

    
}
