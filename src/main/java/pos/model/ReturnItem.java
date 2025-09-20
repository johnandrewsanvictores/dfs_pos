package pos.model;

import javafx.beans.property.*;

public class ReturnItem {
    private final StringProperty productName = new SimpleStringProperty();
    private final DoubleProperty price = new SimpleDoubleProperty();
    private final IntegerProperty qtyPurchased = new SimpleIntegerProperty();
    private final DoubleProperty discount = new SimpleDoubleProperty();
    private final IntegerProperty qtyToReturn = new SimpleIntegerProperty(0);
    private final DoubleProperty refundAmount = new SimpleDoubleProperty();
    private final StringProperty productSku = new SimpleStringProperty();
    
    // Database IDs for processing returns
    private final IntegerProperty invoiceItemId = new SimpleIntegerProperty();
    private final IntegerProperty onlineInventoryItemId = new SimpleIntegerProperty();
    private final IntegerProperty inStoreInventoryId = new SimpleIntegerProperty();
    
    public ReturnItem() {}
    
    public ReturnItem(String productName, String productSku, double price, int qtyPurchased, double discount) {
        this.productName.set(productName);
        this.productSku.set(productSku);
        this.price.set(price);
        this.qtyPurchased.set(qtyPurchased);
        this.discount.set(discount);
        updateRefundAmount();
        
        // Add listener to automatically calculate refund amount when quantity changes
        this.qtyToReturn.addListener((obs, oldVal, newVal) -> updateRefundAmount());
    }
    
    // Constructor with database IDs
    public ReturnItem(String productName, String productSku, double price, int qtyPurchased, double discount,
                     int invoiceItemId, int onlineInventoryItemId, int inStoreInventoryId) {
        this(productName, productSku, price, qtyPurchased, discount);
        this.invoiceItemId.set(invoiceItemId);
        this.onlineInventoryItemId.set(onlineInventoryItemId);
        this.inStoreInventoryId.set(inStoreInventoryId);
    }
    
    private void updateRefundAmount() {
        double itemPrice = price.get();
        double itemDiscount = discount.get();
        int returnQty = qtyToReturn.get();
        
        // Calculate refund amount: (price - discount) * qty to return
        double refund = (itemPrice - itemDiscount) * returnQty;
        refundAmount.set(Math.max(0, refund));
    }
    
    // Property getters
    public StringProperty productNameProperty() { return productName; }
    public StringProperty productSkuProperty() { return productSku; }
    public DoubleProperty priceProperty() { return price; }
    public IntegerProperty qtyPurchasedProperty() { return qtyPurchased; }
    public DoubleProperty discountProperty() { return discount; }
    public IntegerProperty qtyToReturnProperty() { return qtyToReturn; }
    public DoubleProperty refundAmountProperty() { return refundAmount; }
    
    // Value getters
    public String getProductName() { return productName.get(); }
    public String getProductSku() { return productSku.get(); }
    public double getPrice() { return price.get(); }
    public int getQtyPurchased() { return qtyPurchased.get(); }
    public double getDiscount() { return discount.get(); }
    public int getQtyToReturn() { return qtyToReturn.get(); }
    public double getRefundAmount() { return refundAmount.get(); }
    
    // Database ID getters
    public int getInvoiceItemId() { return invoiceItemId.get(); }
    public int getOnlineInventoryItemId() { return onlineInventoryItemId.get(); }
    public int getInStoreInventoryId() { return inStoreInventoryId.get(); }
    
    // Database ID setters
    public void setInvoiceItemId(int id) { this.invoiceItemId.set(id); }
    public void setOnlineInventoryItemId(int id) { this.onlineInventoryItemId.set(id); }
    public void setInStoreInventoryId(int id) { this.inStoreInventoryId.set(id); }
    
    // Value setters
    public void setProductName(String productName) { this.productName.set(productName); }
    public void setProductSku(String productSku) { this.productSku.set(productSku); }
    public void setPrice(double price) { this.price.set(price); updateRefundAmount(); }
    public void setQtyPurchased(int qtyPurchased) { this.qtyPurchased.set(qtyPurchased); }
    public void setDiscount(double discount) { this.discount.set(discount); updateRefundAmount(); }
    public void setQtyToReturn(int qtyToReturn) { 
        // Ensure we don't return more than purchased
        int maxReturn = Math.min(qtyToReturn, this.qtyPurchased.get());
        this.qtyToReturn.set(Math.max(0, maxReturn)); 
    }
    
    public void incrementQtyToReturn() {
        int current = qtyToReturn.get();
        int max = qtyPurchased.get();
        if (current < max) {
            setQtyToReturn(current + 1);
        }
    }
    
    public void decrementQtyToReturn() {
        int current = qtyToReturn.get();
        if (current > 0) {
            setQtyToReturn(current - 1);
        }
    }
}