package utils;

import java.security.PublicKey;

public class Offer {
    
    private PublicKey clientId;
    private String resourceType;
	private int stock;
	private float pricePerUnit;
	
	
	public Offer(String resourceType, int stock, float price) {
		this.setResourceType(resourceType);
		this.setStock(stock);
		this.setPricePerUnit(price);
		
	}


    public String getResourceType() {
        return resourceType;
    }


    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
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


    public void setPricePerUnit(float pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}
