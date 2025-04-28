package pos.model;

import javafx.beans.property.*;

public class CartItem {
    private Product product;
    private IntegerProperty quantity = new SimpleIntegerProperty(1);

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity.set(quantity);
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int qty) { this.quantity.set(qty); }
    public IntegerProperty quantityProperty() { return quantity; }
    public double getSubtotal() { return product.getPrice() * getQuantity(); }
} 