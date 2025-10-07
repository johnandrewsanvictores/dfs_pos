package pos.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import pos.model.CartItem;
import pos.model.Product;
import java.util.*;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class ProductCatalogView extends VBox {
    
    // Constants
    private static final int PRODUCTS_PER_PAGE = 20;
    private static final int MIN_CARD_WIDTH = 180;
    private static final int CARD_SPACING = 10;
    private static final int GRID_PADDING = 10;
    private static final int SEARCH_DEBOUNCE_DELAY_MS = 200;
    private static final double LOADER_SIZE = 60.0;
    private static final double CARD_IMAGE_HEIGHT = 80.0;
    private static final double CARD_IMAGE_PADDING = 40.0;
    
    // Style constants
    private static final String CARD_STYLE = "-fx-background-color: #fff; -fx-border-color: #e0e0e0; -fx-border-radius: 8; -fx-background-radius: 8;";
    private static final String ADD_BUTTON_STYLE = "-fx-background-color: #1976d2; -fx-text-fill: white; -fx-background-radius: 5;";
    private static final String SHORTCUT_BADGE_STYLE = "-fx-background-color: #eee; -fx-padding: 2 8; -fx-border-radius: 4; -fx-border-color: #ccc; -fx-font-size: 10px; -fx-text-fill: #333;";
    private static final String OUT_OF_STOCK_STYLE = "-fx-text-fill: #d32f2f;";
    private static final String LOW_STOCK_STYLE = "-fx-text-fill: #fbc02d;";
    private static final String IN_STOCK_STYLE = "-fx-text-fill: #388e3c;";
    
    // UI Components
    private final GridPane productGrid = new GridPane();
    private final ObservableList<Product> filteredProducts = FXCollections.observableArrayList();
    private final Map<Product, Label> productQuantityLabels = new HashMap<>();
    private final Map<Product, Label> productPriceLabels = new HashMap<>();
    private int currentPage = 1;
    private Label pageLabel;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private ProgressIndicator catalogLoader;
    private final ObservableList<CartItem> cart;
    private final TextField searchField;
    private Product[] allProducts; // Store reference to all products for barcode scanning
    private POSView posView; // Reference to POSView for transaction ID

    public ProductCatalogView(Product[] products, ObservableList<CartItem> cart, POSView posView) {
        this.cart = cart;
        this.searchField = new TextField();
        this.allProducts = products; // Store reference for barcode scanning
        this.posView = posView; // Store POSView reference
        
        initializeComponent();
        initializeFilteredProducts(products);
        
        Label catalogLabel = createCatalogLabel();
        HBox searchBox = createSearchBox();
        setupProductGrid();
        catalogLoader = createLoader();
        StackPane gridStack = createGridStack();
        
        setupPaginationControls();
        HBox paginationBox = createPaginationBox();
        
        setupSearchFunctionality(products);
        setupResponsiveLayout();
        ScrollPane scrollPane = createScrollPane(gridStack);
        setupKeyboardShortcuts();
        
        assembleLayout(catalogLabel, searchBox, scrollPane, paginationBox);
        
        // Initial render
        updateProductGridResponsive(filteredProducts, getWidth());
    }

    private void initializeComponent() {
        setPadding(new Insets(GRID_PADDING));
        getStyleClass().add("card");
        setSpacing(0);
    }

    private void initializeFilteredProducts(Product[] products) {
        filteredProducts.setAll(
            Arrays.stream(products)
                .filter(p -> p.getQuantity() > 0)
                .collect(Collectors.toList())
        );
    }

    private Label createCatalogLabel() {
        Label catalogLabel = new Label("Product Catalog");
        catalogLabel.getStyleClass().add("section-title");
        catalogLabel.setFont(new Font(18));
        VBox.setMargin(catalogLabel, new Insets(0, 0, 10, 0));
        return catalogLabel;
    }

    private HBox createSearchBox() {
        HBox searchBox = new HBox(5);
        
        ImageView searchIcon = new ImageView(getClass().getResource("/img/search.png").toExternalForm());
        searchIcon.setFitWidth(22);
        searchIcon.setFitHeight(22);
        
        searchField.setPromptText("Search products...");
        searchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        Label shortcutBadge = new Label("F1");
        shortcutBadge.setStyle(SHORTCUT_BADGE_STYLE);
        
        searchBox.getChildren().addAll(searchIcon, searchField, shortcutBadge);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        VBox.setMargin(searchBox, new Insets(0, 0, 15, 0));
        
        return searchBox;
    }

    private void setupProductGrid() {
        productGrid.setHgap(CARD_SPACING);
        productGrid.setVgap(CARD_SPACING);
        productGrid.setPadding(new Insets(GRID_PADDING));
    }

    private ProgressIndicator createLoader() {
        ProgressIndicator loader = new ProgressIndicator();
        loader.setMaxSize(LOADER_SIZE, LOADER_SIZE);
        loader.setVisible(false);
        return loader;
    }

    private StackPane createGridStack() {
        StackPane gridStack = new StackPane(productGrid, catalogLoader);
        gridStack.setAlignment(Pos.CENTER);
        return gridStack;
    }

    private void setupPaginationControls() {
        prevPageBtn = new Button("Previous");
        nextPageBtn = new Button("Next");
        pageLabel = new Label();
        
        prevPageBtn.setOnAction(e -> navigateToPreviousPage());
        nextPageBtn.setOnAction(e -> navigateToNextPage());
    }

    private HBox createPaginationBox() {
        HBox paginationBox = new HBox(10, prevPageBtn, pageLabel, nextPageBtn);
        paginationBox.setAlignment(Pos.CENTER);
        VBox.setMargin(paginationBox, new Insets(10, 0, 0, 0));
        return paginationBox;
    }

    private void setupSearchFunctionality(Product[] products) {
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(SEARCH_DEBOUNCE_DELAY_MS));
        searchField.textProperty().addListener((obs, old, val) -> {
            searchDebounce.stop();
            searchDebounce.setOnFinished(e -> performSearch(products, val));
            searchDebounce.playFromStart();
        });
        
        // Enter key functionality for quick add to cart
        searchField.setOnAction(e -> handleSearchEnterKey());
    }

    private void setupResponsiveLayout() {
        widthProperty().addListener((obs, oldVal, newVal) -> {
            updateProductGridResponsive(filteredProducts, newVal.doubleValue());
        });
    }

    private ScrollPane createScrollPane(StackPane gridStack) {
        ScrollPane scrollPane = new ScrollPane(gridStack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return scrollPane;
    }

    private void setupKeyboardShortcuts() {
        // Auto-focus search field when shown
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                setupWindowFocusListener(newScene);
                setupKeyboardShortcuts(newScene);
            }
        });
    }

    private void assembleLayout(Label catalogLabel, HBox searchBox, ScrollPane scrollPane, HBox paginationBox) {
        getChildren().addAll(catalogLabel, searchBox, scrollPane, paginationBox);
    }

    private void navigateToPreviousPage() {
        if (currentPage > 1) {
            currentPage--;
            updateProductGridResponsive(filteredProducts, getWidth());
        }
    }

    private void navigateToNextPage() {
        int maxPage = (int) Math.ceil((double) filteredProducts.size() / PRODUCTS_PER_PAGE);
        if (currentPage < maxPage) {
            currentPage++;
            updateProductGridResponsive(filteredProducts, getWidth());
        }
    }

    private void performSearch(Product[] products, String searchValue) {
        filteredProducts.setAll(
            Arrays.stream(products)
                .filter(p -> p.getSku().toLowerCase().contains(searchValue.toLowerCase()) && p.getQuantity() > 0)
                .collect(Collectors.toList())
        );
        currentPage = 1;
        updateProductGridResponsive(filteredProducts, getWidth());
    }

    private void handleSearchEnterKey() {
        String search = searchField.getText().trim();
        if (search.isEmpty()) {
            return; // Don't process empty search
        }
        
        String searchLower = search.toLowerCase();
        
        // First, search in ALL products for exact SKU match (for barcode scanning)
        Optional<Product> exactMatch = Arrays.stream(allProducts)
            .filter(p -> p.getSku().toLowerCase().equals(searchLower))
            .findFirst();
        
        if (exactMatch.isPresent()) {
            Product product = exactMatch.get();
            if (product.getQuantity() > 0) {
                addProductToCart(product);
                searchField.clear();
            } else {
                // Product found but out of stock
                showOutOfStockDialog(product);
                searchField.clear();
            }
        } else {
            // No exact match found - show error dialog
            showProductNotFoundDialog(search);
            searchField.clear();
        }
    }

    private void setupWindowFocusListener(javafx.scene.Scene newScene) {
        newScene.windowProperty().addListener((o, ow, nw) -> {
            if (nw != null) {
                nw.setOnShown(e -> searchField.requestFocus());
            }
        });
    }

    private void setupKeyboardShortcuts(javafx.scene.Scene newScene) {
        // F1 shortcut to focus search field
        newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ke -> {
            if (ke.getCode() == javafx.scene.input.KeyCode.F1) {
                searchField.requestFocus();
                ke.consume();
            }
        });
    }

    private void addProductToCart(Product product) {
        try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
            // Find if product already exists in cart
            CartItem found = cart.stream()
                .filter(ci -> ci.getProduct().getSku().equals(product.getSku()))
                .findFirst()
                .orElse(null);
            
            if (found != null) {
                // Update existing cart item - use UPSERT for immediate update
                int newQty = found.getQuantity() + 1;
                
                // Single optimized query: check stock and upsert reservation
                boolean success = pos.db.StockReservationDAO.upsertReservation(
                    conn, found.getTransactionId(), product.getSku(), newQty);
                
                if (success) {
                    found.setQuantity(newQty);
                    product.setQuantity(product.getQuantity() - 1);
                    updateQuantityLabel(product);
                }
            } else {
                // Create new cart item with SHARED session transaction ID
                String sessionTransactionId = posView.getCurrentTransactionId();
                CartItem newItem = new CartItem(product, 1, sessionTransactionId);
                
                // Single optimized query: check stock and create reservation
                boolean success = pos.db.StockReservationDAO.upsertReservation(
                    conn, sessionTransactionId, product.getSku(), 1);
                
                if (success) {
                    cart.add(newItem);
                    product.setQuantity(product.getQuantity() - 1);
                    updateQuantityLabel(product);
                }
            }
        } catch (java.sql.SQLException e) {
            // Show user-friendly error message from exception
            showStockUnavailableDialog(product, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Failed to add product to cart: " + e.getMessage());
        }
    }

    private void updateQuantityLabel(Product product) {
        Label quantityLabel = productQuantityLabels.get(product);
        if (quantityLabel != null) {
            quantityLabel.setText("Available: " + product.getQuantity());
            
            if (product.getQuantity() == 0) {
                quantityLabel.setStyle(OUT_OF_STOCK_STYLE);
            } else if (product.getQuantity() <= 3) {
                quantityLabel.setStyle(LOW_STOCK_STYLE);
            } else {
                quantityLabel.setStyle(IN_STOCK_STYLE);
            }
        }
    }
    
    private void showOutOfStockDialog(Product product) {
        Alert alert = createOutOfStockAlert(product);
        alert.showAndWait();
    }

    private Alert createOutOfStockAlert(Product product) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Out of Stock");
        alert.setHeaderText("Cannot Add More Items");
        alert.setContentText(String.format("The product '%s' is out of stock. Cannot add more items to cart.", 
                                          product.getSku()));
        return alert;
    }
    
    private void showProductNotFoundDialog(String barcode) {
        Alert alert = createProductNotFoundAlert(barcode);
        alert.showAndWait();
    }

    private Alert createProductNotFoundAlert(String barcode) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Product Not Found");
        alert.setHeaderText("Barcode Not Recognized");
        alert.setContentText(String.format("No product found with barcode/SKU: '%s'", barcode));
        return alert;
    }
    
    private void showStockUnavailableDialog(Product product, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Stock Unavailable");
        alert.setHeaderText("Cannot Add to Cart");
        alert.setContentText(String.format("Product '%s': %s", product.getDescription(), message));
        alert.showAndWait();
    }
    
    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateProductGridResponsive(ObservableList<Product> products, double width) {
        if (catalogLoader != null) catalogLoader.setVisible(true);
        
        clearProductGrid();
        GridLayoutInfo layoutInfo = calculateGridLayout(products, width);
        updatePaginationControls(layoutInfo);
        
        for (int i = layoutInfo.startIndex; i < layoutInfo.endIndex; i++) {
            Product product = layoutInfo.displayProducts.get(i);
            VBox productCard = createProductCard(product, layoutInfo.cardWidth);
            int gridCol = (i - layoutInfo.startIndex) % layoutInfo.columns;
            int gridRow = (i - layoutInfo.startIndex) / layoutInfo.columns;
            productGrid.add(productCard, gridCol, gridRow);
        }
        
        if (catalogLoader != null) catalogLoader.setVisible(false);
    }

    private void clearProductGrid() {
        productGrid.getChildren().clear();
        productQuantityLabels.clear();
    }

    private GridLayoutInfo calculateGridLayout(ObservableList<Product> products, double width) {
        int cols = Math.max(1, (int) (width / (MIN_CARD_WIDTH + CARD_SPACING)));
        double cardWidth = (width - (cols - 1) * (CARD_SPACING * 2) - (GRID_PADDING * 5)) / cols;
        
        List<Product> displayProducts = products.stream()
            .filter(p -> p.getQuantity() > 0)
            .collect(Collectors.toList());
        
        int totalProducts = displayProducts.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) totalProducts / PRODUCTS_PER_PAGE));
        if (currentPage > maxPage) currentPage = maxPage;
        
        int startIdx = (currentPage - 1) * PRODUCTS_PER_PAGE;
        int endIdx = Math.min(startIdx + PRODUCTS_PER_PAGE, totalProducts);
        
        return new GridLayoutInfo(cols, cardWidth, displayProducts, totalProducts, maxPage, startIdx, endIdx);
    }

    private void updatePaginationControls(GridLayoutInfo layoutInfo) {
        pageLabel.setText("Page " + currentPage + " of " + layoutInfo.maxPage);
        prevPageBtn.setDisable(currentPage == 1);
        nextPageBtn.setDisable(currentPage == layoutInfo.maxPage);
    }

    private VBox createProductCard(Product product, double cardWidth) {
        VBox card = createCardContainer(cardWidth);
        ImageView productImage = createProductImage(product, cardWidth);
        Label productName = createProductNameLabel(product);
        Label productPrice = createProductPriceLabel(product);
        Label quantityLabel = createQuantityLabel(product);
        Button addButton = createAddToCartButton(product);
        
        card.getChildren().addAll(productImage, productName, productPrice, quantityLabel, addButton);
        return card;
    }

    private VBox createCardContainer(double cardWidth) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(CARD_SPACING));
        card.setAlignment(Pos.CENTER);
        card.setStyle(CARD_STYLE);
        card.setPrefWidth(cardWidth);
        card.setMaxWidth(cardWidth);
        card.setMinWidth(cardWidth);
        return card;
    }

    private ImageView createProductImage(Product product, double cardWidth) {
        ImageView img = new ImageView();
        Image placeholder = new Image(getClass().getResourceAsStream("/img/placeholder.jpg"));
        img.setImage(loadProductImage(product, placeholder));
        img.setFitWidth(cardWidth - CARD_IMAGE_PADDING);
        img.setFitHeight(CARD_IMAGE_HEIGHT);
        img.setPreserveRatio(true);
        return img;
    }

    private Image loadProductImage(Product product, Image placeholder) {
        try {
            String imagePath = product.getImagePath();
            if (imagePath != null && (imagePath.startsWith("http://") || imagePath.startsWith("https://"))) {
                Image realImage = new Image(imagePath, true);
                return realImage.isError() ? placeholder : realImage;
            } else if (imagePath != null && !imagePath.isEmpty()) {
                Image realImage = new Image(getClass().getResource(imagePath).toExternalForm(), true);
                return realImage.isError() ? placeholder : realImage;
            }
        } catch (Exception ex) {
            // Return placeholder on any exception
        }
        return placeholder;
    }

    private Label createProductNameLabel(Product product) {
        Label name = new Label(product.getSku());
        name.setFont(new Font(14));
        name.setStyle("-fx-font-weight: bold;");
        return name;
    }

    private Label createProductPriceLabel(Product product) {
        Label price = new Label(String.format("₱%.2f", product.getPrice()));
        price.setFont(new Font(13));
        productPriceLabels.put(product, price); // Store reference for updates
        return price;
    }

    private Label createQuantityLabel(Product product) {
        Label quantityLabel = new Label("Available: " + product.getQuantity());
        productQuantityLabels.put(product, quantityLabel);
        quantityLabel.setFont(new Font(12));
        updateQuantityLabel(product);
        return quantityLabel;
    }

    private Button createAddToCartButton(Product product) {
        Button addBtn = new Button("+ Add to Cart");
        addBtn.setStyle(ADD_BUTTON_STYLE);
        addBtn.setOnAction(e -> addProductToCart(product));
        return addBtn;
    }

    // Helper class to hold grid layout information
    private static class GridLayoutInfo {
        final int columns;
        final double cardWidth;
        final List<Product> displayProducts;
        final int maxPage;
        final int startIndex;
        final int endIndex;

        GridLayoutInfo(int columns, double cardWidth, List<Product> displayProducts, 
                      int totalProducts, int maxPage, int startIndex, int endIndex) {
            this.columns = columns;
            this.cardWidth = cardWidth;
            this.displayProducts = displayProducts;
            this.maxPage = maxPage;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    public Map<Product, Label> getProductQuantityLabels() {
        return productQuantityLabels;
    }

    public void focusSearchField() {
        searchField.requestFocus();
    }
    
    public TextField getSearchField() {
        return searchField;
    }
    
    public void setSearchFieldText(String text) {
        searchField.setText(text);
    }
    
    public void clearSearchField() {
        searchField.clear();
    }
    
    public void triggerSearchEnterAction() {
        handleSearchEnterKey();
    }

    // Add this method to allow hiding 0-quantity products after checkout
    public void refreshAfterCheckout() {
        filteredProducts.setAll(
            filteredProducts.stream()
                .filter(p -> p.getQuantity() > 0)
                .collect(Collectors.toList())
        );
        currentPage = 1;
        updateProductGridResponsive(filteredProducts, getWidth());
    }
    
    /**
     * Handle product updates from POSView polling system
     * Updates the products array and refreshes the UI display
     */
    public void handleProductUpdates(java.util.List<Product> modifiedProducts) {
        boolean needsUIRefresh = false;
        
        // Update the allProducts array
        for (Product modifiedProduct : modifiedProducts) {
            // Find and update in allProducts array
            for (int i = 0; i < allProducts.length; i++) {
                if (allProducts[i].getSku().equals(modifiedProduct.getSku())) {
                    Product oldProduct = allProducts[i];
                    allProducts[i] = modifiedProduct;
                    
                    // Update filtered products if it's currently displayed
                    for (int j = 0; j < filteredProducts.size(); j++) {
                        if (filteredProducts.get(j).getSku().equals(modifiedProduct.getSku())) {
                            filteredProducts.set(j, modifiedProduct);
                            needsUIRefresh = true;
                            break;
                        }
                    }
                    
                    // Update quantity labels if they exist
                    Label quantityLabel = productQuantityLabels.get(oldProduct);
                    if (quantityLabel != null) {
                        // Remove old mapping and add new one
                        productQuantityLabels.remove(oldProduct);
                        productQuantityLabels.put(modifiedProduct, quantityLabel);
                        
                        quantityLabel.setText("Available: " + modifiedProduct.getQuantity());
                        
                        // Update quantity label style based on stock level
                        if (modifiedProduct.getQuantity() <= 0) {
                            quantityLabel.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3;");
                        } else if (modifiedProduct.getQuantity() <= 5) {
                            quantityLabel.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3;");
                        } else {
                            quantityLabel.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3;");
                        }
                    }
                    
                    // Update price labels if they exist
                    Label priceLabel = productPriceLabels.get(oldProduct);
                    if (priceLabel != null) {
                        // Remove old mapping and add new one
                        productPriceLabels.remove(oldProduct);
                        productPriceLabels.put(modifiedProduct, priceLabel);
                        
                        priceLabel.setText(String.format("₱%.2f", modifiedProduct.getPrice()));
                    }
                    
                    break;
                }
            }
        }
        
        // Refresh the UI if any displayed products were updated
        if (needsUIRefresh) {
            updateProductGridResponsive(filteredProducts, getWidth());
        }
    }
    
    /**
     * Handle new products being added to the system
     */
    public void handleNewProducts(java.util.List<Product> newProducts) {
        // Add new products to the arrays
        Product[] expandedAllProducts = new Product[allProducts.length + newProducts.size()];
        System.arraycopy(allProducts, 0, expandedAllProducts, 0, allProducts.length);
        
        for (int i = 0; i < newProducts.size(); i++) {
            expandedAllProducts[allProducts.length + i] = newProducts.get(i);
        }
        
        allProducts = expandedAllProducts;
        
        // Refresh the display to show new products
        refreshView();
        
        System.out.println("Product catalog updated with " + newProducts.size() + " new products");
    }
    
    /**
     * Handle archived products being removed from the system
     */
    public void handleArchivedProducts(java.util.List<String> archivedSkus) {
        // Remove archived products from allProducts array
        java.util.List<Product> activeProducts = new java.util.ArrayList<>();
        for (Product product : allProducts) {
            if (!archivedSkus.contains(product.getSku())) {
                activeProducts.add(product);
            } else {
                // Clean up label mappings for archived products
                productQuantityLabels.remove(product);
                productPriceLabels.remove(product);
            }
        }
        
        allProducts = activeProducts.toArray(new Product[0]);
        
        // Refresh the display to remove archived products
        refreshView();
        
        System.out.println("Product catalog updated: removed " + archivedSkus.size() + " archived products");
    }
    
    /**
     * Refresh the entire view (used for major changes like adding/removing products)
     */
    private void refreshView() {
        // Re-initialize filtered products with all active products
        initializeFilteredProducts(allProducts);
        // Reset to first page
        currentPage = 1;
        // Update the grid display
        updateProductGridResponsive(filteredProducts, getWidth());
    }
    
    // Public method to allow barcode scanning to add products directly to cart
    public void addProductToCartDirectly(Product product) {
        addProductToCart(product);
    }
} 