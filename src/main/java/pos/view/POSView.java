package pos.view;

import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import pos.model.CartItem;
import pos.model.Product;
import pos.db.ProductDAO;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class POSView extends BorderPane {
    private final Product[] products;
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Label dateLabel = new Label();
    private final Label timeLabel = new Label();
    private final Runnable onLogout;
    private final String cashierName;

    public POSView(Runnable onLogout, String cashierName) {
        this.onLogout = onLogout;
        this.cashierName = cashierName;
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #fff;");
        setTop(buildHeader());

        // Load products from database
        List<Product> productList = new ArrayList<>();
        try (ResultSet rs = ProductDAO.getAllActiveProducts()) {
            while (rs.next()) {
                String sku = rs.getString("sku");
                double price = rs.getDouble("unit_price");
                String description = rs.getString("description");
                String imagePath = rs.getString("image_path");
                System.out.println(imagePath);
                if (imagePath != null && !imagePath.isEmpty()) {
                    imagePath = "http://localhost/dream_fashion_shop/assets/uploads/product_img/" + imagePath;
                    System.out.println(imagePath);
                }
                int quantity = rs.getInt("quantity");
                productList.add(new Product(sku, price, description, imagePath, quantity));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        products = productList.toArray(new Product[0]);

        // Use an HBox for the main content area
        HBox mainContent = new HBox(10);
        ProductCatalogView productCatalog = new ProductCatalogView(products, cart);
        CartView cartView = new CartView(cart, productCatalog.getProductQuantityLabels());
        PaymentSectionView paymentSection = new PaymentSectionView(cart, products, productCatalog::refreshAfterCheckout);
        mainContent.getChildren().addAll(productCatalog, cartView, paymentSection);
        // Set HGrow priorities
        HBox.setHgrow(productCatalog, Priority.ALWAYS);
        HBox.setHgrow(cartView, Priority.ALWAYS);
        HBox.setHgrow(paymentSection, Priority.ALWAYS);
        // Set preferred widths as percentages (will be relative to the HBox width)
        mainContent.widthProperty().addListener((obs, oldVal, newVal) -> {
            double total = newVal.doubleValue();
            productCatalog.setPrefWidth(total * 0.5);
            cartView.setPrefWidth(total * 0.3);
            paymentSection.setPrefWidth(total * 0.2);
        });
        setCenter(mainContent);
        // Focus search field as soon as scene is ready
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                javafx.application.Platform.runLater(productCatalog::focusSearchField);
            }
        });
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                newScene.getWindow().setOnShown(e -> {
                    if (newScene.getWindow() instanceof javafx.stage.Stage) {
                        javafx.stage.Stage stage = (javafx.stage.Stage) newScene.getWindow();
                        stage.setMaximized(false); // Don't force maximize
                    }
                });
            }
        });
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
        if (dateLabel != null && !getChildren().contains(dateLabel)) {
            getChildren().add(dateLabel);
        }
        if (timeLabel != null && !getChildren().contains(timeLabel)) {
            getChildren().add(timeLabel);
        }
        if (dateLabel != null && timeLabel != null) {
            double width = getWidth();
            double height = getHeight();
            double dateWidth = dateLabel.prefWidth(-1);
            double dateHeight = dateLabel.prefHeight(-1);
            double timeWidth = timeLabel.prefWidth(-1);
            double timeHeight = timeLabel.prefHeight(-1);
            double maxWidth = Math.max(dateWidth, timeWidth);
            double totalHeight = dateHeight + timeHeight + 5;
            double x = width - maxWidth - 30;
            double y = height - totalHeight - 20;
            dateLabel.resizeRelocate(x, y, maxWidth, dateHeight);
            timeLabel.resizeRelocate(x, y + dateHeight + 5, maxWidth, timeHeight);
        }
    }
} 