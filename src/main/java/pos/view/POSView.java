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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.concurrent.Task;
import java.util.concurrent.atomic.AtomicLong;

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
    
    // Barcode scanning fields
    private StringBuilder barcodeBuffer = new StringBuilder();
    private AtomicLong lastKeystrokeTime = new AtomicLong(0);
    private int fastKeystrokeCount = 0; // Count of consecutive fast keystrokes
    private ProductCatalogView productCatalog; // Store reference for barcode searches
    private PaymentSectionView paymentSection; // Store reference for returns mode

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
            // Build main content
            mainContent = new HBox(10);
            productCatalog = new ProductCatalogView(products, cart); // Store reference
            CartView cartView = new CartView(cart, productCatalog.getProductQuantityLabels());
            paymentSection = new PaymentSectionView(cart, products, productCatalog::refreshAfterCheckout, staffId, cashierName, dateLabel, timeLabel);
            
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
} 