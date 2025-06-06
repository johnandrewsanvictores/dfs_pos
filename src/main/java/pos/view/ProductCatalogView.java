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
    private final GridPane productGrid = new GridPane();
    private final ObservableList<Product> filteredProducts;
    private final Map<Product, Label> productQuantityLabels = new HashMap<>();
    private int currentPage = 1;
    private final int productsPerPage = 20;
    private Label pageLabel;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private ProgressIndicator catalogLoader;
    private double lastCatalogWidth = 800;
    private final ObservableList<CartItem> cart;
    private final TextField searchField;

    public ProductCatalogView(Product[] products, ObservableList<CartItem> cart) {
        this.cart = cart;
        setPadding(new Insets(10));
        getStyleClass().add("card");
        Label catalogLabel = new Label("Product Catalog");
        catalogLabel.getStyleClass().add("section-title");
        catalogLabel.setFont(new Font(18));
        VBox.setMargin(catalogLabel, new Insets(0, 0, 10, 0));
        HBox searchBox = new HBox(5);
        ImageView searchIcon = new ImageView(getClass().getResource("/img/search.png").toExternalForm());
        searchIcon.setFitWidth(22);
        searchIcon.setFitHeight(22);
        searchField = new TextField();
        searchField.setPromptText("Search products...");
        searchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Label shortcutBadge = new Label("F1");
        shortcutBadge.setStyle(
            "-fx-background-color: #eee;" +
            "-fx-padding: 2 8;" +
            "-fx-border-radius: 4;" +
            "-fx-border-color: #ccc;" +
            "-fx-font-size: 10px;" +
            "-fx-text-fill: #333;"
        );
        searchBox.getChildren().addAll(searchIcon, searchField, shortcutBadge);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        VBox.setMargin(searchBox, new Insets(0, 0, 15, 0));
        productGrid.setHgap(10);
        productGrid.setVgap(10);
        productGrid.setPadding(new Insets(10));
        filteredProducts = FXCollections.observableArrayList(
            Arrays.stream(products).filter(p -> p.getQuantity() > 0).collect(Collectors.toList())
        );
        catalogLoader = new ProgressIndicator();
        catalogLoader.setMaxSize(60, 60);
        catalogLoader.setVisible(false);
        StackPane gridStack = new StackPane(productGrid, catalogLoader);
        gridStack.setAlignment(Pos.CENTER);
        prevPageBtn = new Button("Previous");
        nextPageBtn = new Button("Next");
        pageLabel = new Label();
        prevPageBtn.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                updateProductGridResponsive(filteredProducts, getWidth());
            }
        });
        nextPageBtn.setOnAction(e -> {
            int maxPage = (int) Math.ceil((double) filteredProducts.size() / productsPerPage);
            if (currentPage < maxPage) {
                currentPage++;
                updateProductGridResponsive(filteredProducts, getWidth());
            }
        });
        HBox paginationBox = new HBox(10, prevPageBtn, pageLabel, nextPageBtn);
        paginationBox.setAlignment(Pos.CENTER);
        VBox.setMargin(paginationBox, new Insets(10, 0, 0, 0));
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));
        searchField.textProperty().addListener((obs, old, val) -> {
            searchDebounce.stop();
            searchDebounce.setOnFinished(e -> {
                filteredProducts.setAll(
                    Arrays.stream(products)
                        .filter(p -> p.getSku().toLowerCase().contains(val.toLowerCase()) && p.getQuantity() > 0)
                        .collect(Collectors.toList())
                );
                currentPage = 1;
                updateProductGridResponsive(filteredProducts, getWidth());
            });
            searchDebounce.playFromStart();
        });
        widthProperty().addListener((obs, oldVal, newVal) -> {
            lastCatalogWidth = newVal.doubleValue();
            updateProductGridResponsive(filteredProducts, lastCatalogWidth);
        });
        lastCatalogWidth = 800;
        updateProductGridResponsive(filteredProducts, lastCatalogWidth);
        ScrollPane scrollPane = new ScrollPane(gridStack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(catalogLabel, searchBox, scrollPane, paginationBox);
        setSpacing(0);

        // --- Keyboard and focus enhancements ---
        // Auto-focus search field when shown
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((o, ow, nw) -> {
                    if (nw != null) {
                        nw.setOnShown(e -> searchField.requestFocus());
                    }
                });
                // F1 shortcut to focus search field
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ke -> {
                    if (ke.getCode() == javafx.scene.input.KeyCode.F1) {
                        searchField.requestFocus();
                        ke.consume();
                    }
                });
            }
        });
        // Enter in search field: add first matching product to cart
        searchField.setOnAction(e -> {
            String search = searchField.getText().trim().toLowerCase();
            Optional<Product> match = filteredProducts.stream()
                .filter(p -> p.getSku().toLowerCase().equals(search) && p.getQuantity() > 0)
                .findFirst();
            if (match.isPresent()) {
                Product p = match.get();
                CartItem found = cart.stream().filter(ci -> ci.getProduct().getSku().equals(p.getSku())).findFirst().orElse(null);
                if (found != null) {
                    found.setQuantity(found.getQuantity() + 1);
                } else {
                    cart.add(new CartItem(p, 1));
                }
                p.setQuantity(p.getQuantity() - 1);
                Label qLabel = productQuantityLabels.get(p);
                if (qLabel != null) {
                    qLabel.setText("Available: " + p.getQuantity());
                    if (p.getQuantity() == 0) {
                        qLabel.setStyle("-fx-text-fill: #d32f2f;");
                    } else if (p.getQuantity() <= 3) {
                        qLabel.setStyle("-fx-text-fill: #fbc02d;");
                    } else {
                        qLabel.setStyle("-fx-text-fill: #388e3c;");
                    }
                }
                // Clear the search field after adding to cart
                searchField.clear();
            }
        });
    }

    private void updateProductGridResponsive(ObservableList<Product> products, double width) {
        if (catalogLoader != null) catalogLoader.setVisible(true);
        productGrid.getChildren().clear();
        productQuantityLabels.clear();
        int minCardWidth = 180;
        int cols = Math.max(1, (int) (width / (minCardWidth + 10)));
        double cardWidth = (width - (cols - 1) * 20 - 55) / cols;
        List<Product> displayProducts = products.stream().filter(p -> p.getQuantity() > 0).collect(Collectors.toList());
        int totalProducts = displayProducts.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) totalProducts / productsPerPage));
        if (currentPage > maxPage) currentPage = maxPage;
        int startIdx = (currentPage - 1) * productsPerPage;
        int endIdx = Math.min(startIdx + productsPerPage, totalProducts);
        pageLabel.setText("Page " + currentPage + " of " + maxPage);
        prevPageBtn.setDisable(currentPage == 1);
        nextPageBtn.setDisable(currentPage == maxPage);
        for (int i = startIdx; i < endIdx; i++) {
            Product p = displayProducts.get(i);
            VBox card = new VBox(8);
            card.setPadding(new Insets(10));
            card.setAlignment(Pos.CENTER);
            card.setStyle("-fx-background-color: #fff; -fx-border-color: #e0e0e0; -fx-border-radius: 8; -fx-background-radius: 8;");
            card.setPrefWidth(cardWidth);
            card.setMaxWidth(cardWidth);
            card.setMinWidth(cardWidth);
            ImageView img = new ImageView();
            // Use a placeholder image while loading
            Image placeholder = new Image(getClass().getResourceAsStream("/img/placeholder.jpg"));
            img.setImage(placeholder);
            try {
                Image realImage;
                String imagePath = p.getImagePath();
                if (imagePath != null && (imagePath.startsWith("http://") || imagePath.startsWith("https://"))) {
                    realImage = new Image(imagePath, true);
                } else if (imagePath != null && !imagePath.isEmpty()) {
                    realImage = new Image(getClass().getResource(imagePath).toExternalForm(), true);
                } else {
                    realImage = placeholder;
                }
                if (realImage.isError()) {
                    realImage = placeholder;
                }
                img.setImage(realImage);
            } catch (Exception ex) {
                img.setImage(placeholder);
            }
            img.setFitWidth(cardWidth - 40);
            img.setFitHeight(80);
            img.setPreserveRatio(true);
            Label name = new Label(p.getSku());
            name.setFont(new Font(14));
            name.setStyle("-fx-font-weight: bold;");
            Label price = new Label(String.format("₱%.2f", p.getPrice()));
            price.setFont(new Font(13));
            Label quantityLabel = new Label("Available: " + p.getQuantity());
            productQuantityLabels.put(p, quantityLabel);
            quantityLabel.setFont(new Font(12));
            if (p.getQuantity() == 0) {
                quantityLabel.setStyle("-fx-text-fill: #d32f2f;");
            } else if (p.getQuantity() <= 3) {
                quantityLabel.setStyle("-fx-text-fill: #fbc02d;");
            } else {
                quantityLabel.setStyle("-fx-text-fill: #388e3c;");
            }
            Button addBtn = new Button("+ Add to Cart");
            addBtn.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; -fx-background-radius: 5;");
            addBtn.setOnAction(e -> {
                if (p.getQuantity() > 0) {
                    CartItem found = cart.stream().filter(ci -> ci.getProduct().getSku().equals(p.getSku())).findFirst().orElse(null);
                    if (found != null) {
                        found.setQuantity(found.getQuantity() + 1);
                    } else {
                        cart.add(new CartItem(p, 1));
                    }
                    p.setQuantity(p.getQuantity() - 1);
                    Label qLabel = productQuantityLabels.get(p);
                    if (qLabel != null) {
                        qLabel.setText("Available: " + p.getQuantity());
                        if (p.getQuantity() == 0) {
                            qLabel.setStyle("-fx-text-fill: #d32f2f;");
                        } else if (p.getQuantity() <= 3) {
                            qLabel.setStyle("-fx-text-fill: #fbc02d;");
                        } else {
                            qLabel.setStyle("-fx-text-fill: #388e3c;");
                        }
                    }
                }
            });
            card.getChildren().addAll(img, name, price, quantityLabel, addBtn);
            productGrid.add(card, (i - startIdx) % cols, (i - startIdx) / cols);
        }
        if (catalogLoader != null) catalogLoader.setVisible(false);
    }

    public Map<Product, Label> getProductQuantityLabels() {
        return productQuantityLabels;
    }

    public void focusSearchField() {
        searchField.requestFocus();
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
} 