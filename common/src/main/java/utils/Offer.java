package utils;

import java.security.PublicKey;

public class Offer {
    
    private final PublicKey clientId;
    private final String resourceType;
	private int stock;
	private final float pricePerUnit;
	
	
	public Offer(PublicKey cID, String resourceType, int stock, float price) {
        this.clientId = cID;
		this.resourceType = resourceType;
		this.decreaseStock(stock);
		this.pricePerUnit = price;
		
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

}
