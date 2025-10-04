package pos.view;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import pos.model.CartItem;
import pos.model.Product;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.collections.ListChangeListener;

public class CartView extends VBox {
    
    // Constants
    private static final int DEFAULT_PADDING = 10;
    private static final int LABEL_FONT_SIZE = 18;
    private static final int BUTTON_SPACING = 5;
    private static final int LOW_STOCK_THRESHOLD = 3;
    
    // Column widths
    private static final double NAME_COLUMN_WIDTH = 110.0;
    private static final double PRICE_COLUMN_WIDTH = 60.0;
    private static final double QTY_COLUMN_WIDTH = 40.0;
    private static final double TOTAL_COLUMN_WIDTH = 70.0;
    private static final double ACTION_COLUMN_WIDTH = 70.0;
    
    // Style constants
    private static final String REMOVE_BUTTON_STYLE = "-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-background-radius: 5;";
    private static final String OUT_OF_STOCK_STYLE = "-fx-text-fill: #d32f2f;";
    private static final String LOW_STOCK_STYLE = "-fx-text-fill: #fbc02d;";
    private static final String IN_STOCK_STYLE = "-fx-text-fill: #388e3c;";
    
    // Button labels
    private static final String PLUS_BUTTON_TEXT = "+";
    private static final String MINUS_BUTTON_TEXT = "-";
    private static final String REMOVE_BUTTON_TEXT = "x";
    
    private final TableView<CartItem> cartTable;
    private final ObservableList<CartItem> cart;
    private final Map<Product, Label> productQuantityLabels;

    public CartView(ObservableList<CartItem> cart, Map<Product, Label> productQuantityLabels) {
        this.cart = cart;
        this.productQuantityLabels = productQuantityLabels;
        
        initializeComponent();
        Label cartLabel = createCartLabel();
        cartTable = createCartTable();
        assembleLayout(cartLabel);
    }

    private void initializeComponent() {
        setPadding(new Insets(DEFAULT_PADDING));
        getStyleClass().add("card");
        setSpacing(0);
    }

    private void assembleLayout(Label cartLabel) {
        VBox.setMargin(cartLabel, new Insets(0, 0, DEFAULT_PADDING, 0));
        getChildren().addAll(cartLabel, cartTable);
        VBox.setVgrow(cartTable, Priority.ALWAYS);
    }

    private Label createCartLabel() {
        Label cartLabel = new Label("Shopping Cart");
        cartLabel.getStyleClass().add("section-title");
        cartLabel.setFont(new Font(LABEL_FONT_SIZE));
        return cartLabel;
    }

    private TableView<CartItem> createCartTable() {
        TableView<CartItem> table = new TableView<>(cart);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().clear();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        TableColumn<CartItem, String> nameCol = createNameColumn();
        TableColumn<CartItem, Double> priceCol = createPriceColumn();
        TableColumn<CartItem, Integer> qtyCol = createQtyColumn();
        TableColumn<CartItem, Double> subtotalCol = createSubtotalColumn();
        TableColumn<CartItem, Void> actionCol = createActionColumn();
        
        addColumnsToTable(table, nameCol, priceCol, qtyCol, subtotalCol, actionCol);
        return table;
    }

    private void addColumnsToTable(TableView<CartItem> table, 
                                  TableColumn<CartItem, String> nameCol,
                                  TableColumn<CartItem, Double> priceCol, 
                                  TableColumn<CartItem, Integer> qtyCol,
                                  TableColumn<CartItem, Double> subtotalCol, 
                                  TableColumn<CartItem, Void> actionCol) {
        table.getColumns().add(nameCol);
        table.getColumns().add(priceCol);
        table.getColumns().add(qtyCol);
        table.getColumns().add(subtotalCol);
        table.getColumns().add(actionCol);
    }

    private TableColumn<CartItem, String> createNameColumn() {
        TableColumn<CartItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getProduct().getSku()));
        nameCol.setPrefWidth(NAME_COLUMN_WIDTH);
        return nameCol;
    }

    private TableColumn<CartItem, Double> createPriceColumn() {
        TableColumn<CartItem, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getProduct().getPrice()).asObject());
        priceCol.setPrefWidth(PRICE_COLUMN_WIDTH);
        return priceCol;
    }

    private TableColumn<CartItem, Integer> createQtyColumn() {
        TableColumn<CartItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        qtyCol.setPrefWidth(QTY_COLUMN_WIDTH);
        return qtyCol;
    }

    private TableColumn<CartItem, Double> createSubtotalColumn() {
        TableColumn<CartItem, Double> subtotalCol = new TableColumn<>("Total");
        subtotalCol.setCellValueFactory(cell -> createSubtotalValueFactory(cell));
        subtotalCol.setPrefWidth(TOTAL_COLUMN_WIDTH);
        subtotalCol.setCellFactory(col -> createSubtotalCellFactory());
        return subtotalCol;
    }

    private javafx.beans.property.ReadOnlyObjectWrapper<Double> createSubtotalValueFactory(TableColumn.CellDataFeatures<CartItem, Double> cell) {
        CartItem item = cell.getValue();
        return new javafx.beans.property.ReadOnlyObjectWrapper<>(item.getSubtotal());
    }

    private TableCell<CartItem, Double> createSubtotalCellFactory() {
        return new TableCell<CartItem, Double>() {
            private CartItem lastItem = null;
            private javafx.beans.value.ChangeListener<Number> listener = null;

            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);

                cleanupPreviousListener();

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    resetItemAndListener();
                } else {
                    setupNewItemListener();
                }
            }

            private void cleanupPreviousListener() {
                if (lastItem != null && listener != null) {
                    lastItem.quantityProperty().removeListener(listener);
                }
            }

            private void resetItemAndListener() {
                lastItem = null;
                listener = null;
            }

            private void setupNewItemListener() {
                CartItem item = (CartItem) getTableRow().getItem();
                setText(String.format("₱%.2f", item.getSubtotal()));
                listener = (obs, oldVal, newVal) -> setText(String.format("₱%.2f", item.getSubtotal()));
                item.quantityProperty().addListener(listener);
                lastItem = item;
            }
        };
    }

    private TableColumn<CartItem, Void> createActionColumn() {
        TableColumn<CartItem, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(ACTION_COLUMN_WIDTH);
        actionCol.setCellFactory(col -> createActionCellFactory());
        return actionCol;
    }

    private TableCell<CartItem, Void> createActionCellFactory() {
        return new TableCell<>() {
            private final HBox buttonContainer;
            
            {
                buttonContainer = createButtonContainer(this);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttonContainer);
            }
        };
    }

    private HBox createButtonContainer(TableCell<CartItem, Void> cell) {
        HBox container = new HBox(BUTTON_SPACING);
        
        Button plusBtn = createActionButton(PLUS_BUTTON_TEXT, () -> handlePlusAction(cell.getIndex()));
        Button minusBtn = createActionButton(MINUS_BUTTON_TEXT, () -> handleMinusAction(cell.getIndex()));
        Button removeBtn = createRemoveButton(() -> handleRemoveAction(cell.getIndex()));
        
        container.getChildren().addAll(plusBtn, minusBtn, removeBtn);
        return container;
    }

    private Button createActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("cart-action-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button createRemoveButton(Runnable action) {
        Button removeBtn = new Button(REMOVE_BUTTON_TEXT);
        removeBtn.getStyleClass().add("cart-action-button");
        removeBtn.setStyle(REMOVE_BUTTON_STYLE);
        removeBtn.setOnAction(e -> action.run());
        return removeBtn;
    }

    private void handlePlusAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product product = item.getProduct();
        
        // OPTIMIZED: Use single upsert query
        try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
            int newQty = item.getQuantity() + 1;
            
            // Single query: check stock and update/insert reservation
            boolean success = pos.db.StockReservationDAO.upsertReservation(
                conn, item.getTransactionId(), product.getSku(), newQty);
            
            if (success) {
                incrementCartItem(item, product);
            }
        } catch (java.sql.SQLException e) {
            // Show user-friendly error from SQL exception
            showStockUnavailableDialog(product, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Failed to update cart: " + e.getMessage());
        }
    }

    private void handleMinusAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product product = item.getProduct();
        
        if (canDecrementCartItem(item)) {
            // OPTIMIZED: Use single upsert query
            try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
                int newQty = item.getQuantity() - 1;
                
                // Single query: update reservation quantity
                pos.db.StockReservationDAO.upsertReservation(
                    conn, item.getTransactionId(), product.getSku(), newQty);
                
                decrementCartItem(item, product);
            } catch (Exception e) {
                e.printStackTrace();
                showErrorDialog("Failed to update cart: " + e.getMessage());
            }
        }
    }

    private void handleRemoveAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product product = item.getProduct();
        
        // OPTIMIZED: Single query delete by transaction_id + SKU
        try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
            // Release the reservation
            pos.db.StockReservationDAO.removeReservation(
                conn, item.getTransactionId(), product.getSku());
            
            removeCartItem(item, product);
        } catch (Exception e) {
            e.printStackTrace();
            // Still remove from cart even if reservation release fails
            removeCartItem(item, product);
        }
    }

    private boolean canAddToCart(Product product) {
        return product.getQuantity() > 0;
    }

    private boolean canDecrementCartItem(CartItem item) {
        return item.getQuantity() > 1;
    }

    private void incrementCartItem(CartItem item, Product product) {
        item.setQuantity(item.getQuantity() + 1);
        product.setQuantity(product.getQuantity() - 1);
        updateProductQuantityLabel(product);
    }

    private void decrementCartItem(CartItem item, Product product) {
        item.setQuantity(item.getQuantity() - 1);
        product.setQuantity(product.getQuantity() + 1);
        updateProductQuantityLabel(product);
    }

    private void removeCartItem(CartItem item, Product product) {
        product.setQuantity(product.getQuantity() + item.getQuantity());
        cart.remove(item);
        updateProductQuantityLabel(product);
    }

    private void updateProductQuantityLabel(Product product) {
        Label quantityLabel = productQuantityLabels.get(product);
        if (quantityLabel != null) {
            updateQuantityLabelText(quantityLabel, product);
            updateQuantityLabelStyle(quantityLabel, product);
        }
    }

    private void updateQuantityLabelText(Label label, Product product) {
        label.setText("Available: " + product.getQuantity());
    }

    private void updateQuantityLabelStyle(Label label, Product product) {
        int quantity = product.getQuantity();
        
        if (quantity == 0) {
            label.setStyle(OUT_OF_STOCK_STYLE);
        } else if (quantity <= LOW_STOCK_THRESHOLD) {
            label.setStyle(LOW_STOCK_STYLE);
        } else {
            label.setStyle(IN_STOCK_STYLE);
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
    
    private void showStockUnavailableDialog(Product product, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Stock Unavailable");
        alert.setHeaderText("Cannot Add More Items");
        alert.setContentText(String.format("Product '%s': %s", product.getSku(), message));
        alert.showAndWait();
    }
    
    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Operation Failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setupCartListeners(Label subtotalSummary, Label totalSummary) {
        Runnable updateTotals = createTotalsUpdater(subtotalSummary, totalSummary);
        setupCartChangeListener(updateTotals);
        setupExistingItemListeners(updateTotals);
        updateTotals.run(); // Initial update
    }

    private Runnable createTotalsUpdater(Label subtotalSummary, Label totalSummary) {
        return () -> {
            double sum = calculateCartTotal();
            updateSummaryLabels(subtotalSummary, totalSummary, sum);
        };
    }

    private double calculateCartTotal() {
        return cart.stream().mapToDouble(CartItem::getSubtotal).sum();
    }

    private void updateSummaryLabels(Label subtotalSummary, Label totalSummary, double sum) {
        String formattedTotal = String.format("%.2f", sum);
        subtotalSummary.setText("Subtotal: ₱" + formattedTotal);
        totalSummary.setText("Total: ₱" + formattedTotal);
    }

    private void setupCartChangeListener(Runnable updateTotals) {
        cart.addListener((ListChangeListener<CartItem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    addQuantityListenersToNewItems(change.getAddedSubList(), updateTotals);
                }
            }
            updateTotals.run();
        });
    }

    private void addQuantityListenersToNewItems(java.util.List<? extends CartItem> addedItems, Runnable updateTotals) {
        for (CartItem item : addedItems) {
            item.quantityProperty().addListener((obs, oldVal, newVal) -> updateTotals.run());
        }
    }

    private void setupExistingItemListeners(Runnable updateTotals) {
        for (CartItem item : cart) {
            item.quantityProperty().addListener((obs, oldVal, newVal) -> updateTotals.run());
        }
    }
} 