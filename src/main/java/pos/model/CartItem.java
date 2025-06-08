package pos.model;

import javafx.beans.property.*;

public class CartItem {
    private Product product;
    private IntegerProperty quantity = new SimpleIntegerProperty(1);
    private double discount = 0.0;
    private double discountedTotal = 0.0;
    private String appliedPromo = null;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity.set(quantity);
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
} 