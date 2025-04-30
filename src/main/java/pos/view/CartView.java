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
    private final TableView<CartItem> cartTable;
    private final ObservableList<CartItem> cart;
    private final Map<Product, Label> productQuantityLabels;

    public CartView(ObservableList<CartItem> cart, Map<Product, Label> productQuantityLabels) {
        this.cart = cart;
        this.productQuantityLabels = productQuantityLabels;
        setPadding(new Insets(10));
        getStyleClass().add("card");
        Label cartLabel = createCartLabel();
        VBox.setMargin(cartLabel, new Insets(0, 0, 10, 0));
        cartTable = createCartTable();
        getChildren().addAll(cartLabel, cartTable);
        VBox.setVgrow(cartTable, Priority.ALWAYS);
        setSpacing(0); // Ensure consistent spacing
    }

    private Label createCartLabel() {
        Label cartLabel = new Label("Shopping Cart");
        cartLabel.getStyleClass().add("section-title");
        cartLabel.setFont(new Font(18));
        return cartLabel;
    }

    private TableView<CartItem> createCartTable() {
        TableView<CartItem> table = new TableView<>(cart);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().clear();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<CartItem, String> nameCol = createNameColumn();
        TableColumn<CartItem, Double> priceCol = createPriceColumn();
        TableColumn<CartItem, Integer> qtyCol = createQtyColumn();
        TableColumn<CartItem, Double> subtotalCol = createSubtotalColumn();
        TableColumn<CartItem, Void> actionCol = createActionColumn();
        table.getColumns().setAll(nameCol, priceCol, qtyCol, subtotalCol, actionCol);
        return table;
    }

    private TableColumn<CartItem, String> createNameColumn() {
        TableColumn<CartItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getProduct().getSku()));
        nameCol.setPrefWidth(110);
        return nameCol;
    }

    private TableColumn<CartItem, Double> createPriceColumn() {
        TableColumn<CartItem, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getProduct().getPrice()).asObject());
        priceCol.setPrefWidth(60);
        return priceCol;
    }

    private TableColumn<CartItem, Integer> createQtyColumn() {
        TableColumn<CartItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        qtyCol.setPrefWidth(40);
        return qtyCol;
    }

    private TableColumn<CartItem, Double> createSubtotalColumn() {
        TableColumn<CartItem, Double> subtotalCol = new TableColumn<>("Total");
        subtotalCol.setCellValueFactory(cell -> {
            CartItem item = cell.getValue();
            // Bind to quantityProperty so the cell updates when quantity changes
            return new javafx.beans.property.ReadOnlyObjectWrapper<>(item.getSubtotal());
        });
        subtotalCol.setPrefWidth(70);

        // Add a cell factory to update the value when quantity changes
        subtotalCol.setCellFactory(col -> new TableCell<CartItem, Double>() {
            private CartItem lastItem = null;
            private javafx.beans.value.ChangeListener<Number> listener = null;

            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);

                if (lastItem != null && listener != null) {
                    lastItem.quantityProperty().removeListener(listener);
                }

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    lastItem = null;
                    listener = null;
                } else {
                    CartItem item = (CartItem) getTableRow().getItem();
                    setText(String.format("₱%.2f", item.getSubtotal()));
                    listener = (obs, oldVal, newVal) -> setText(String.format("₱%.2f", item.getSubtotal()));
                    item.quantityProperty().addListener(listener);
                    lastItem = item;
                }
            }
        });

        return subtotalCol;
    }

    private TableColumn<CartItem, Void> createActionColumn() {
        TableColumn<CartItem, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(70);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(5);
            private final Button plus = new Button("+");
            private final Button minus = new Button("-");
            private final Button remove = new Button("x");
            {
                plus.getStyleClass().add("cart-action-button");
                minus.getStyleClass().add("cart-action-button");
                remove.getStyleClass().add("cart-action-button");
                plus.setOnAction(e -> handlePlusAction(getIndex()));
                minus.setOnAction(e -> handleMinusAction(getIndex()));
                remove.setOnAction(e -> handleRemoveAction(getIndex()));
                remove.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-background-radius: 5;");
                box.getChildren().addAll(plus, minus, remove);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        return actionCol;
    }

    private void handlePlusAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product p = item.getProduct();
        if (p.getQuantity() > 0) {
            item.setQuantity(item.getQuantity() + 1);
            p.setQuantity(p.getQuantity() - 1);
            updateProductQuantityLabel(p);
        }
    }

    private void handleMinusAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product p = item.getProduct();
        if (item.getQuantity() > 1) {
            item.setQuantity(item.getQuantity() - 1);
            p.setQuantity(p.getQuantity() + 1);
            updateProductQuantityLabel(p);
        }
    }

    private void handleRemoveAction(int index) {
        CartItem item = cartTable.getItems().get(index);
        Product p = item.getProduct();
        p.setQuantity(p.getQuantity() + item.getQuantity());
        cart.remove(item);
        updateProductQuantityLabel(p);
    }

    private void updateProductQuantityLabel(Product p) {
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

    public void setupCartListeners(Label subtotalSummary, Label totalSummary) {
        Runnable updateTotals = () -> {
            double sum = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
            subtotalSummary.setText("Subtotal: ₱" + String.format("%.2f", sum));
            totalSummary.setText("Total: ₱" + String.format("%.2f", sum));
        };
        cart.addListener((ListChangeListener<CartItem>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (CartItem item : c.getAddedSubList()) {
                        item.quantityProperty().addListener((obs, oldVal, newVal) -> updateTotals.run());
                    }
                }
            }
            updateTotals.run();
        });
        for (CartItem item : cart) {
            item.quantityProperty().addListener((obs, oldVal, newVal) -> updateTotals.run());
        }
        updateTotals.run();
    }
} 