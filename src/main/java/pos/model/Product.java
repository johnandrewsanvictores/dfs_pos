package pos.model;

public class Product {
    private String name;
    private double price;
    private String description;
    private String imagePath;
    private int quantity;

    public Product(String name, double price, String description, String imagePath, int quantity) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.quantity = quantity;
    }

    // For backward compatibility (if needed)
    public Product(String name, double price, String description, String imagePath) {
        this(name, price, description, imagePath, 0);
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getDescription() { return description; }
    public String getImagePath() { return imagePath; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
} 