package pos.model;

public class Product {
    private String sku;
    private double price;
    private String description;
    private String imagePath;
    private int quantity;

    public Product(String sku, double price, String description, String imagePath, int quantity) {
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.quantity = quantity;
    }

    // For backward compatibility (if needed)
    public Product(String sku, double price, String description, String imagePath) {
        this(sku, price, description, imagePath, 0);
    }

    public String getSku() { return sku; }
    public double getPrice() { return price; }
    public String getDescription() { return description; }
    public String getImagePath() { return imagePath; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
} 