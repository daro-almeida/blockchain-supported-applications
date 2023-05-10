package utils;

import java.security.PublicKey;

public class Want {

    private final PublicKey clientId;
    private final String resourceType;
	private int quantity;
	private final float pricePerUnit;
	
	
	public Want(String resourceType, int quantity, float price) {
		this.setResourceType(resourceType);
		this.setQuantity(quantity);
		this.setPricePerUnit(price);
		
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
