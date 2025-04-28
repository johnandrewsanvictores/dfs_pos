package pos.view;

import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import pos.model.CartItem;
import pos.model.Product;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class POSView extends BorderPane {
    private final Product[] products = {
        new Product("Apple", 1.00, "Fresh red apple", "/img/bag.jpg", 10),
        new Product("Banana", 0.75, "Yellow ripe banana", "/img/bag.jpg", 8),
        new Product("Bag", 25.00, "Leather handbag", "/img/bag.jpg", 5),
        new Product("Orange", 1.20, "Fresh juicy orange", "/img/bag.jpg", 12),
        new Product("Strawberry", 3.00, "Fresh strawberries", "/img/bag.jpg", 7),
        new Product("Mango", 1.50, "Sweet mango", "/img/bag.jpg", 9),
        new Product("Pineapple", 2.00, "Ripe pineapple", "/img/bag.jpg", 6),
        new Product("Watermelon", 4.00, "Fresh watermelon", "/img/bag.jpg", 4),
        new Product("Grapes", 2.50, "Seedless grapes", "/img/bag.jpg", 11),
        new Product("Peach", 1.80, "Sweet and juicy peach", "/img/bag.jpg", 3),
        new Product("Laptop", 500.00, "High-performance laptop", "/img/bag.jpg", 2),
        new Product("Phone", 300.00, "Latest smartphone", "/img/bag.jpg", 15),
        new Product("Headphones", 80.00, "Noise-cancelling headphones", "/img/bag.jpg", 13),
        new Product("Charger", 25.00, "Fast charging cable", "/img/bag.jpg", 14),
        new Product("TV", 700.00, "Smart TV", "/img/bag.jpg", 1),
        new Product("Refrigerator", 400.00, "Energy-efficient refrigerator", "/img/bag.jpg", 7),
        new Product("Washing Machine", 350.00, "Front-loading washing machine", "/img/bag.jpg", 8),
        new Product("Microwave", 100.00, "Compact microwave oven", "/img/bag.jpg", 6),
        new Product("Toaster", 40.00, "Electric toaster", "/img/bag.jpg", 5),
        new Product("Fan", 30.00, "Oscillating fan", "/img/bag.jpg", 10),
        new Product("Chair", 50.00, "Comfortable office chair", "/img/bag.jpg", 9),
        new Product("Table", 120.00, "Wooden dining table", "/img/bag.jpg", 12),
        new Product("Lamp", 25.00, "LED desk lamp", "/img/bag.jpg", 11)
    };

    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Label dateLabel = new Label();
    private final Label timeLabel = new Label();
    private final Runnable onLogout;

    public POSView(Runnable onLogout) {
        this.onLogout = onLogout;
        setPadding(new Insets(10));
        setTop(buildHeader());
        // Use an HBox for the main content area
        HBox mainContent = new HBox(10);
        ProductCatalogView productCatalog = new ProductCatalogView(products, cart);
        CartView cartView = new CartView(cart, productCatalog.getProductQuantityLabels());
        PaymentSectionView paymentSection = new PaymentSectionView(cart, products, () -> {});
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
        Label cashier = new Label("Cashier: John Andrew San Victores");
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