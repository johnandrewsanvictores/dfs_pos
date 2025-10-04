package pos.model;

import javafx.beans.property.*;
import java.util.UUID;

public class CartItem {
    private Product product;
    private IntegerProperty quantity = new SimpleIntegerProperty(1);
    private double discount = 0.0;
    private double discountedTotal = 0.0;
    private String appliedPromo = null;
    private String transactionId; // Shared session transaction ID

    /**
     * @deprecated Use CartItem(Product, int, String) with explicit transaction ID
     */
    @Deprecated
    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity.set(quantity);
        // Generate unique transaction ID (deprecated - should use shared session ID)
        this.transactionId = UUID.randomUUID().toString();
    }
    
    /**
     * Preferred constructor - uses shared session transaction ID
     */
    public CartItem(Product product, int quantity, String transactionId) {
        this.product = product;
        this.quantity.set(quantity);
        this.transactionId = transactionId;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int qty) { this.quantity.set(qty); }
    public IntegerProperty quantityProperty() { return quantity; }
    public double getSubtotal() { return product.getPrice() * getQuantity(); }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public double getDiscountedTotal() { return discountedTotal; }
    public void setDiscountedTotal(double discountedTotal) { this.discountedTotal = discountedTotal; }
    public String getAppliedPromo() { return appliedPromo; }
    public void setAppliedPromo(String appliedPromo) { this.appliedPromo = appliedPromo; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
} 