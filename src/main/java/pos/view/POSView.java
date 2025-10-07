package pos.view;

import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import pos.model.CartItem;
import pos.model.Product;
import pos.db.DBCredentials;
import pos.db.ProductDAO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.concurrent.Task;
import java.util.concurrent.atomic.AtomicLong;
import java.sql.Timestamp;
import java.sql.SQLException;
import javafx.application.Platform;

public class POSView extends BorderPane {
    // Barcode scanning constants
    private static final int BARCODE_MIN_LENGTH = 8;
    private static final int BARCODE_MAX_LENGTH = 20;
    private static final long BARCODE_INPUT_TIMEOUT_MS = 50; // Time between keystrokes for barcode
    private static final int BARCODE_MIN_CHARS_FOR_DETECTION = 5; // Minimum chars typed fast to consider barcode
    
    // Application fields
    private Product[] products;
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Label dateLabel = new Label();
    private final Label timeLabel = new Label();
    private final Runnable onLogout;
    private final String cashierName;
    private final int staffId;
    private final StackPane skeletonOverlay = new StackPane();
    private final VBox skeletonBox = new VBox();
    private HBox mainContent;
    
    // Cart session transaction ID - shared across all cart items until checkout
    private String currentTransactionId = generateReadableTransactionId();
    
    // Barcode scanning fields
    private StringBuilder barcodeBuffer = new StringBuilder();
    private AtomicLong lastKeystrokeTime = new AtomicLong(0);
    private int fastKeystrokeCount = 0; // Count of consecutive fast keystrokes
    private ProductCatalogView productCatalog; // Store reference for barcode searches
    private PaymentSectionView paymentSection; // Store reference for returns mode
    
    // Product polling fields
    private Timestamp lastProductCheck;
    private Timeline productUpdateTimeline;
    private Map<String, Product> lastKnownProducts = new HashMap<>(); // Cache to prevent infinite loops
    private int debugCounter = 0; // Limit debug output frequency
    private int connectionErrorCount = 0; // Track consecutive connection errors
    
    /**
     * Generate a readable transaction ID in format: TXN-YYYYMMDD-HHMMSS-XXX
     * Example: TXN-20241005-143022-A4B
     */
    private String generateReadableTransactionId() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String timestamp = now.format(dateFormat);
        // Add 3 random alphanumeric characters for uniqueness
        String randomSuffix = String.format("%03X", (int)(Math.random() * 4096));
        return "TXN-" + timestamp + "-" + randomSuffix;
    }
    
    /**
     * Get the current transaction ID for this cart session
     */
    public String getCurrentTransactionId() {
        return currentTransactionId;
    }
    
    /**
     * Reset transaction ID after successful checkout
     */
    public void resetTransactionId() {
        this.currentTransactionId = generateReadableTransactionId();
        System.out.println("New transaction ID generated: " + currentTransactionId);
    }
    
    /**
     * Release all stock reservations for the current cart session.
     * Called when logging out or closing the application.
     */
    public void releaseAllCartReservations() {
        if (cart.isEmpty()) {
            return; // No items in cart, nothing to release
        }
        
        try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
            // Collect all unique transaction IDs from cart
            List<String> transactionIds = cart.stream()
                .map(CartItem::getTransactionId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
            
            if (!transactionIds.isEmpty()) {
                int released = pos.db.StockReservationDAO.clearReservationsByTransactions(conn, transactionIds);
                System.out.println("Released " + released + " stock reservations for " + transactionIds.size() + " transaction(s)");
            }
        } catch (Exception e) {
            System.err.println("Error releasing cart reservations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public POSView(Runnable onLogout, String cashierName, int staffId) {
        this.onLogout = onLogout;
        this.cashierName = cashierName;
        this.staffId = staffId;
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #fff;");
        setTop(buildHeader());

        // --- Skeleton Loader ---
        skeletonBox.setAlignment(Pos.CENTER);
        skeletonBox.setSpacing(20);
        Label skeletonLabel = new Label("Loading products...");
        skeletonLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: #888;");
        ProgressIndicator skeletonLoader = new ProgressIndicator();
        skeletonLoader.setPrefSize(60, 60);
        skeletonBox.getChildren().addAll(skeletonLoader, skeletonLabel);
        skeletonOverlay.getChildren().add(skeletonBox);
        setCenter(skeletonOverlay);

        // --- Load products in background ---
        Task<List<Product>> loadProductsTask = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                List<Product> productList = new ArrayList<>();
                for (Product p : ProductDAO.getAllActiveProductsAsList()) {
                    String imagePath = p.getImagePath();
                    if (imagePath != null && !imagePath.isEmpty()) {
                        imagePath = DBCredentials.BASE_URL +"/assets/uploads/product_img/" + imagePath;
                        p = new Product(p.getSku(), p.getPrice(), p.getDescription(), imagePath, p.getQuantity(), p.getCategoryId());
                    }
                    productList.add(p);
                }
                return productList;
            }
        };
        loadProductsTask.setOnSucceeded(ev -> {
            products = loadProductsTask.getValue().toArray(new Product[0]);
            
            // Initialize last check timestamp
            try {
                lastProductCheck = ProductDAO.getCurrentDatabaseTimestamp();
            } catch (Exception e) {
                lastProductCheck = new Timestamp(System.currentTimeMillis());
                System.err.println("Could not get DB timestamp, using system time: " + e.getMessage());
            }
            
            // Build main content
            mainContent = new HBox(10);
            productCatalog = new ProductCatalogView(products, cart, this); // Pass POSView for transaction ID
            CartView cartView = new CartView(cart, productCatalog.getProductQuantityLabels());
            paymentSection = new PaymentSectionView(cart, products, productCatalog::refreshAfterCheckout, staffId, cashierName, dateLabel, timeLabel, this);
            
            // Set up returns mode toggle
            paymentSection.setOnReturnsModeToggle(() -> toggleReturnsMode(paymentSection, cartView));
            
            mainContent.getChildren().addAll(productCatalog, cartView, paymentSection);
            HBox.setHgrow(productCatalog, Priority.ALWAYS);
            HBox.setHgrow(cartView, Priority.ALWAYS);
            HBox.setHgrow(paymentSection, Priority.ALWAYS);
            mainContent.widthProperty().addListener((obs, oldVal, newVal) -> {
                double total = newVal.doubleValue();
                productCatalog.setPrefWidth(total * 0.5);
                cartView.setPrefWidth(total * 0.3);
                paymentSection.setPrefWidth(total * 0.2);
            });
            setCenter(mainContent);
            
            // Setup global barcode listener after UI is ready
            setupGlobalBarcodeListener();
            
            // Setup product polling after UI is ready
            setupProductPolling();
        });
        new Thread(loadProductsTask).start();

        dateLabel.setFont(new Font(24));
        dateLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
        dateLabel.getStyleClass().add("date-time");
        timeLabel.setFont(new Font(32));
        timeLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
        timeLabel.getStyleClass().add("date-time");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a");
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            LocalDateTime now = LocalDateTime.now();
            dateLabel.setText(now.format(dateFormatter));
            timeLabel.setText(now.format(timeFormatter));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        
        // Setup periodic cleanup of expired reservations (every 5 minutes)
        setupReservationCleanupTask();
    }

    public Label getDateLabel() {
        return dateLabel;
    }

    public Label getTimeLabel() {
        return timeLabel;
    }

    private HBox buildHeader() {
        ImageView logo = new ImageView(getClass().getResource("/img/logo.jpg").toExternalForm());
        logo.setFitWidth(70);
        logo.setFitHeight(70);
        Label title = new Label("Point-of-Sale System");
        title.getStyleClass().add("pos-title");
        Label cashier = new Label("Cashier: " + cashierName);
        cashier.setFont(new Font(16));
        cashier.setStyle("-fx-text-fill: #333; -fx-padding: 0 20 0 40;");
        Button logoutBtn = new Button("Log Out");
        logoutBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 5;");
        logoutBtn.setOnAction(e -> {
            // Stop product polling timeline
            dispose();
            // Release all cart reservations before logging out
            releaseAllCartReservations();
            if (onLogout != null) onLogout.run();
        });
        HBox rightBox = new HBox(15, cashier, logoutBtn);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(rightBox, Priority.ALWAYS);
        HBox header = new HBox(30, logo, title, rightBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 0, 20, 0));
        return header;
    }

    @Override
    public void layoutChildren() {
        super.layoutChildren();
        // Time and date are now handled by PaymentSectionView
    }

    // Barcode scanning methods
    private void setupGlobalBarcodeListener() {
        // Add listener to the scene when it becomes available
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                setupBarcodeSceneListener(newScene);
            }
        });
        
        // If scene is already available, set it up immediately
        if (getScene() != null) {
            setupBarcodeSceneListener(getScene());
        }
    }
    
    private void setupBarcodeSceneListener(javafx.scene.Scene scene) {
        // Use addEventFilter instead of setOnKeyPressed to intercept events before they reach other handlers
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPress);
    }
    
    private void handleGlobalKeyPress(KeyEvent event) {
        // Check if we're in normal mode or returns mode
        boolean isInNormalMode = productCatalog != null && mainContent != null && 
                                mainContent.getChildren().contains(productCatalog);
        boolean isInReturnsMode = paymentSection != null && paymentSection.isInReturnsMode();
        
        // Only handle keys if we're in normal mode or returns mode
        if (!isInNormalMode && !isInReturnsMode) {
            return;
        }
        
        // Check what currently has focus
        javafx.scene.Node focusedNode = getScene().getFocusOwner();
        boolean isSearchFieldFocused = isInNormalMode && focusedNode == productCatalog.getSearchField();
        boolean isOtherTextFieldFocused = (focusedNode instanceof TextField || focusedNode instanceof TextArea) && !isSearchFieldFocused;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastKeystroke = currentTime - lastKeystrokeTime.get();
        
        // Reset buffer and counter if too much time has passed (slow typing)
        if (timeSinceLastKeystroke > BARCODE_INPUT_TIMEOUT_MS) {
            barcodeBuffer.setLength(0);
            fastKeystrokeCount = 0;
        }
        
        lastKeystrokeTime.set(currentTime);
        
        // Handle Enter key
        if (event.getCode() == KeyCode.ENTER) {
            if (barcodeBuffer.length() > 0 && fastKeystrokeCount >= BARCODE_MIN_CHARS_FOR_DETECTION) {
                // We have enough fast characters for barcode, process it
                if (isInReturnsMode) {
                    processPotentialBarcodeToReturns();
                } else {
                    processPotentialBarcodeToSearchField();
                }
                event.consume();
                return;
            } else if (!isSearchFieldFocused && !isOtherTextFieldFocused) {
                // No text field focused and no barcode - consume to prevent logout
                event.consume();
                return;
            }
            // Otherwise let normal Enter behavior work (search field, etc.)
            return;
        }
        
        String character = event.getText();
        if (character == null || character.isEmpty() || !isValidBarcodeCharacter(character)) {
            return; // Not a valid barcode character
        }
        
        // Check if this keystroke is fast enough to be part of barcode input
        boolean isFastKeystroke = timeSinceLastKeystroke <= BARCODE_INPUT_TIMEOUT_MS;
        
        if (isFastKeystroke) {
            // Increment fast keystroke counter and add to buffer
            fastKeystrokeCount++;
            barcodeBuffer.append(character);
            
            // Only start barcode mode if we have enough fast characters
            if (fastKeystrokeCount >= BARCODE_MIN_CHARS_FOR_DETECTION) {
                // Clear search field if this is when we first detect barcode input (normal mode only)
                if (isInNormalMode && fastKeystrokeCount == BARCODE_MIN_CHARS_FOR_DETECTION && isSearchFieldFocused) {
                    productCatalog.clearSearchField();
                }
                
                // Auto-process if we've reached max length
                if (barcodeBuffer.length() >= BARCODE_MAX_LENGTH) {
                    if (isInReturnsMode) {
                        processPotentialBarcodeToReturns();
                    } else {
                        processPotentialBarcodeToSearchField();
                    }
                }
                
                event.consume(); // Consume barcode input
            }
            // If we haven't reached minimum chars yet, let it pass through as normal typing
        } else {
            // Slow keystroke - reset counters and let normal text input work
            barcodeBuffer.setLength(0);
            fastKeystrokeCount = 0;
        }
    }
    
    private boolean isValidBarcodeCharacter(String character) {
        // Most barcodes contain numbers, letters, and some special characters
        return character.matches("[0-9A-Za-z\\-_.]");
    }
    
    private void processPotentialBarcodeToSearchField() {
        String potentialBarcode = barcodeBuffer.toString().trim();
        barcodeBuffer.setLength(0); // Clear buffer
        
        // Validate barcode length
        if (potentialBarcode.length() < BARCODE_MIN_LENGTH || 
            potentialBarcode.length() > BARCODE_MAX_LENGTH) {
            return;
        }
        
        // Focus search field and put barcode text in it, then trigger search action
        if (productCatalog != null) {
            productCatalog.focusSearchField();
            productCatalog.setSearchFieldText(potentialBarcode);
            productCatalog.triggerSearchEnterAction();
        }
    }
    
    private void processPotentialBarcodeToReturns() {
        String potentialBarcode = barcodeBuffer.toString().trim();
        barcodeBuffer.setLength(0); // Clear buffer
        fastKeystrokeCount = 0; // Reset counter
        
        // Validate barcode length
        if (potentialBarcode.length() < BARCODE_MIN_LENGTH || 
            potentialBarcode.length() > BARCODE_MAX_LENGTH) {
            return;
        }
        
        // Try to find and increment the item in returns
        if (paymentSection != null) {
            ReturnsManager.ScanResult result = paymentSection.handleReturnsBarcodeScanned(potentialBarcode);
            
            // Show feedback to user based on result
            switch (result) {
                case SUCCESS:
                    // Optional: Show success feedback (could add a toast/notification here)
                    System.out.println("Item " + potentialBarcode + " added to returns");
                    break;
                    
                case ALREADY_AT_MAX:
                    // Show dialog that item is already at maximum return quantity
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                    alert.setTitle("Maximum Quantity Reached");
                    alert.setHeaderText(null);
                    alert.setContentText("Item '" + potentialBarcode + "' is already at maximum return quantity. All purchased items are already selected for return.");
                    alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
                    alert.showAndWait();
                    break;
                    
                case NOT_FOUND:
                    // Show alert that item wasn't found in the original invoice
                    javafx.scene.control.Alert notFoundAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                    notFoundAlert.setTitle("Item Not Found");
                    notFoundAlert.setHeaderText(null);
                    notFoundAlert.setContentText("Item '" + potentialBarcode + "' was not found in the original invoice.");
                    notFoundAlert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
                    notFoundAlert.showAndWait();
                    break;
                    
                case INVALID_BARCODE:
                default:
                    // Invalid barcode - could show error or just ignore
                    break;
            }
        }
    }
    
    private void toggleReturnsMode(PaymentSectionView paymentSection, CartView cartView) {
        if (paymentSection.isInReturnsMode()) {
            // Entering returns mode - hide product catalog, show returns cart
            if (mainContent.getChildren().contains(productCatalog)) {
                mainContent.getChildren().remove(productCatalog);
            }
            
            // Get returns table and replace cart view
            TableView<?> returnsTable = paymentSection.getReturnsTable();
            if (returnsTable != null) {
                // Create a wrapper with clean light background
                VBox returnsWrapper = new VBox();
                returnsWrapper.getChildren().add(returnsTable);
                returnsWrapper.setStyle("-fx-background-color: #f9f9f9; -fx-padding: 5; -fx-background-radius: 5;");
                
                // Make table fill the wrapper
                VBox.setVgrow(returnsTable, Priority.ALWAYS);
                
                // Replace cart view with returns table
                int cartIndex = mainContent.getChildren().indexOf(cartView);
                if (cartIndex != -1) {
                    mainContent.getChildren().set(cartIndex, returnsWrapper);
                }
                
                // Update growth properties
                HBox.setHgrow(returnsWrapper, Priority.ALWAYS);
            }
        } else {
            // Exiting returns mode - restore product catalog and normal cart
            // Find returns table wrapper and replace with cart view
            for (int i = 0; i < mainContent.getChildren().size(); i++) {
                if (mainContent.getChildren().get(i) instanceof VBox) {
                    VBox vbox = (VBox) mainContent.getChildren().get(i);
                    if (vbox.getStyle().contains("#f9f9f9")) { // Returns wrapper
                        mainContent.getChildren().set(i, cartView);
                        HBox.setHgrow(cartView, Priority.ALWAYS);
                        break;
                    }
                }
            }
            
            // Add back product catalog if not present
            if (!mainContent.getChildren().contains(productCatalog)) {
                mainContent.getChildren().add(0, productCatalog);
                HBox.setHgrow(productCatalog, Priority.ALWAYS);
            }
        }
    }
    
    /**
     * Setup a periodic task to clean up expired stock reservations.
     * Runs every 5 minutes to free up reserved stock that has expired.
     */
    private void setupReservationCleanupTask() {
        Timeline cleanupTimeline = new Timeline(new KeyFrame(Duration.minutes(5), event -> {
            // Run cleanup in background thread to avoid blocking UI
            Task<Integer> cleanupTask = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
                        return pos.db.StockReservationDAO.cleanupExpiredReservations(conn);
                    }
                }
            };
            
            cleanupTask.setOnSucceeded(e -> {
                Integer cleaned = cleanupTask.getValue();
                if (cleaned != null && cleaned > 0) {
                    System.out.println("Cleaned up " + cleaned + " expired stock reservations");
                }
            });
            
            cleanupTask.setOnFailed(e -> {
                Throwable exception = cleanupTask.getException();
                System.err.println("Failed to cleanup expired reservations: " + exception.getMessage());
                exception.printStackTrace();
            });
            
            new Thread(cleanupTask).start();
        }));
        
        cleanupTimeline.setCycleCount(Timeline.INDEFINITE);
        cleanupTimeline.play();
        
        // Also run an initial cleanup when the app starts
        Task<Integer> initialCleanupTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
                    return pos.db.StockReservationDAO.cleanupExpiredReservations(conn);
                }
            }
        };
        
        initialCleanupTask.setOnSucceeded(e -> {
            Integer cleaned = initialCleanupTask.getValue();
            if (cleaned != null && cleaned > 0) {
                System.out.println("Initial cleanup: Removed " + cleaned + " expired reservations");
            }
        });
        
        new Thread(initialCleanupTask).start();
    }
    
    /**
     * Setup product polling to check for quantity changes every 10 seconds
     */
    private void setupProductPolling() {
        // Initialize the cache with current products
        for (Product product : products) {
            lastKnownProducts.put(product.getSku(), new Product(
                product.getSku(), product.getPrice(), product.getDescription(),
                product.getImagePath(), product.getQuantity(), product.getCategoryId()
            ));
        }
        
        productUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            // Skip polling if too many consecutive connection errors
            if (connectionErrorCount >= 3) {
                System.err.println("Product polling temporarily disabled due to connection issues. Will retry in next cycle.");
                connectionErrorCount = 0; // Reset counter to try again
                return;
            }
            
            // Run in background thread to avoid blocking UI
            Task<List<Product>> checkUpdatesTask = new Task<>() {
                @Override
                protected List<Product> call() throws Exception {
                    try {
                        return ProductDAO.getModifiedProductsSince(lastProductCheck);
                    } catch (SQLException e) {
                        // Log the error and return empty list to prevent crashes
                        System.err.println("Database connection error during polling: " + e.getMessage());
                        throw e; // Re-throw to be caught by setOnFailed
                    }
                }
            };
            
            checkUpdatesTask.setOnSucceeded(e -> {
                // Reset connection error counter on successful operation
                connectionErrorCount = 0;
                
                List<Product> currentProducts = checkUpdatesTask.getValue();
                if (currentProducts != null && !currentProducts.isEmpty()) {
                    // Compare with cached products to find actual changes
                    List<Product> actuallyModified = new ArrayList<>();
                    
                    for (Product currentProduct : currentProducts) {
                        Product cachedProduct = lastKnownProducts.get(currentProduct.getSku());
                        if (cachedProduct != null) {
                            // Only consider it modified if there's a significant change
                            boolean quantityChanged = cachedProduct.getQuantity() != currentProduct.getQuantity();
                            boolean priceChanged = Math.abs(cachedProduct.getPrice() - currentProduct.getPrice()) > 0.01;
                            
                            if (quantityChanged || priceChanged) {
                                actuallyModified.add(currentProduct);
                                // Update cache to prevent repeated processing
                                lastKnownProducts.put(currentProduct.getSku(), new Product(
                                    currentProduct.getSku(), currentProduct.getPrice(), currentProduct.getDescription(),
                                    currentProduct.getImagePath(), currentProduct.getQuantity(), currentProduct.getCategoryId()
                                ));
                            }
                        }
                    }
                    
                    if (!actuallyModified.isEmpty()) {
                        // Run UI updates on JavaFX Application Thread
                        Platform.runLater(() -> handleProductUpdates(actuallyModified));
                    }
                    
                    // Update timestamp for next check (only on successful operation)
                    try {
                        lastProductCheck = ProductDAO.getCurrentDatabaseTimestamp();
                    } catch (SQLException ex) {
                        System.err.println("Could not update timestamp (will retry next cycle): " + ex.getMessage());
                        // Don't crash - just continue with the old timestamp
                    }
                }
            });
            
            checkUpdatesTask.setOnFailed(e -> {
                connectionErrorCount++;
                Throwable exception = checkUpdatesTask.getException();
                System.err.println("Product update check failed (attempt " + connectionErrorCount + "/3): " + exception.getMessage());
                
                if (connectionErrorCount >= 3) {
                    System.err.println("Too many connection failures. Product polling will pause for one cycle.");
                }
            });
            
            new Thread(checkUpdatesTask).start();
        }));
        
        productUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        productUpdateTimeline.play();
        System.out.println("Product polling started - checking every 30 seconds (conservative frequency to prevent connection issues)");
    }
    
    /**
     * Handle product updates by refreshing the products array and UI
     */
    private void handleProductUpdates(List<Product> modifiedProducts) {
        debugCounter++;
        
        // Only show detailed debug every 3rd update to reduce console spam
        boolean showDetailedDebug = debugCounter % 3 == 1;
        
        if (showDetailedDebug) {
            System.out.println("Found " + modifiedProducts.size() + " modified products");
        }
        
        // Update the products array
        for (Product modifiedProduct : modifiedProducts) {
            for (int i = 0; i < products.length; i++) {
                if (products[i].getSku().equals(modifiedProduct.getSku())) {
                    // Store old values for debugging
                    Product oldProduct = products[i];
                    
                    // Update image path if needed
                    String imagePath = modifiedProduct.getImagePath();
                    if (imagePath != null && !imagePath.isEmpty()) {
                        imagePath = DBCredentials.BASE_URL + "/assets/uploads/product_img/" + imagePath;
                        modifiedProduct = new Product(
                            modifiedProduct.getSku(), 
                            modifiedProduct.getPrice(), 
                            modifiedProduct.getDescription(), 
                            imagePath, 
                            modifiedProduct.getQuantity(), 
                            modifiedProduct.getCategoryId()
                        );
                    }
                    
                    // Print detailed update information for debugging (limited frequency)
                    if (showDetailedDebug) {
                        System.out.println("╔═══ PRODUCT UPDATE DEBUG ═══");
                        System.out.println("║ SKU: " + modifiedProduct.getSku());
                        System.out.println("║ Description: " + modifiedProduct.getDescription());
                        System.out.println("║ Old Quantity: " + oldProduct.getQuantity() + " → New Quantity: " + modifiedProduct.getQuantity());
                        System.out.println("║ Old Price: $" + String.format("%.2f", oldProduct.getPrice()) + " → New Price: $" + String.format("%.2f", modifiedProduct.getPrice()));
                        
                        if (oldProduct.getQuantity() != modifiedProduct.getQuantity()) {
                            int quantityChange = modifiedProduct.getQuantity() - oldProduct.getQuantity();
                            System.out.println("║ Quantity Change: " + (quantityChange > 0 ? "+" : "") + quantityChange);
                            if (modifiedProduct.getQuantity() <= 0) {
                                System.out.println("║ ⚠️  WARNING: Product is now OUT OF STOCK!");
                            } else if (modifiedProduct.getQuantity() <= 5) {
                                System.out.println("║ ⚠️  WARNING: Low stock level!");
                            }
                        }
                        if (Math.abs(oldProduct.getPrice() - modifiedProduct.getPrice()) > 0.01) {
                            double priceChange = modifiedProduct.getPrice() - oldProduct.getPrice();
                            System.out.println("║ Price Change: " + (priceChange > 0 ? "+$" : "-$") + String.format("%.2f", Math.abs(priceChange)));
                        }
                        System.out.println("╚═══════════════════════════");
                    } else {
                        // Simplified output for frequent updates
                        System.out.println("Product update: " + modifiedProduct.getSku() + " (Qty: " + modifiedProduct.getQuantity() + ", Price: $" + String.format("%.2f", modifiedProduct.getPrice()) + ")");
                    }
                    
                    products[i] = modifiedProduct;
                    break;
                }
            }
        }
        
        // Notify product catalog to refresh display if it exists
        if (productCatalog != null) {
            productCatalog.handleProductUpdates(modifiedProducts);
        }
    }
    
    /**
     * Clean up timelines and resources when view is disposed
     */
    public void dispose() {
        if (productUpdateTimeline != null) {
            productUpdateTimeline.stop();
            System.out.println("Product polling timeline stopped");
        }
    }
} 