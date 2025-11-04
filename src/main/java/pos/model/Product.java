package pos.model;

public class Product {
    private String sku;
    private double price;
    private String description;
    private String imagePath;
    private int quantity;
    private int categoryId;
    // New optional display fields
    private String itemName;    // Human-friendly item name from inventory.item_name
    private String colorName;   // Optional color name if applicable (online variants)

    public Product(String sku, double price, String description, String imagePath, int quantity, int categoryId) {
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.quantity = quantity;
        this.categoryId = categoryId;
        this.itemName = null;
        this.colorName = null;
    }

    // For backward compatibility (if needed)
    public Product(String sku, double price, String description, String imagePath) {
        this(sku, price, description, imagePath, 0, 0);
    }

    // New full constructor to include display fields
    public Product(String sku, double price, String description, String imagePath,
                   int quantity, int categoryId, String itemName, String colorName) {
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.quantity = quantity;
        this.categoryId = categoryId;
        this.itemName = itemName;
        this.colorName = colorName;
    }

    public String getSku() { return sku; }
    public double getPrice() { return price; }
    public String getDescription() { return description; }
    public String getImagePath() { return imagePath; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getCategoryId() { return categoryId; }

    // Optional fields
    public String getItemName() { return itemName; }
    public String getColorName() { return colorName; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
} 