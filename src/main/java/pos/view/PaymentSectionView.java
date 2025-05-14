package pos.view;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import pos.model.CartItem;
import pos.model.Product;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.util.Arrays;
import java.util.stream.Collectors;
import javafx.collections.ListChangeListener;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.text.TextAlignment;
import java.io.File;
import java.io.IOException;
import pos.db.PosTransactionDAO;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pos.db.ProductDAO;

public class PaymentSectionView extends VBox {
    private final int staffId;
    private final String cashierName;

    public PaymentSectionView(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted, int staffId, String cashierName) {
        this.staffId = staffId;
        this.cashierName = cashierName;
        setPadding(new Insets(10));
        setPrefWidth(300);
        getStyleClass().add("card");
        Label paymentLabel = new Label("Payment & Summary");
        paymentLabel.getStyleClass().add("section-title");
        VBox.setMargin(paymentLabel, new Insets(0, 0, 10, 0));
        Label paymentMethodLabel = new Label("Payment Method:");
        ComboBox<String> paymentMethod = new ComboBox<>();
        paymentMethod.getItems().setAll("Cash", "E-Wallet");
        paymentMethod.setValue("Cash");
        Label amountPaidLabel = new Label("Amount Paid");
        TextField amountField = new TextField();
        amountField.setPromptText("");
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        Label changeLabel = new Label();
        changeLabel.setFont(new Font(9));
        changeLabel.setStyle("-fx-text-fill: green;");
        VBox summaryBox = new VBox(5);
        summaryBox.getStyleClass().add("section");
        Label subtotalSummary = new Label();
        subtotalSummary.getStyleClass().add("payment-summary");
        Label discountSummary = new Label("Discount: ₱0.00");
        Label totalSummary = new Label();
        totalSummary.getStyleClass().add("payment-summary");
        Label changeSummary = new Label();
        changeSummary.getStyleClass().add("payment-summary");
        subtotalSummary.setFont(new Font(14));
        discountSummary.setFont(new Font(14));
        totalSummary.setFont(new Font(16));
        totalSummary.setStyle("-fx-font-weight: bold;");
        changeSummary.setFont(new Font(14));
        summaryBox.getChildren().addAll(subtotalSummary, discountSummary, totalSummary, changeSummary);
        setupCartListeners(subtotalSummary, totalSummary, cart);
        changeSummary.textProperty().bind(changeLabel.textProperty());
        Runnable updateChange = () -> {
            double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
            String amtStr = amountField.getText();
            if (amtStr.isEmpty()) {
                changeLabel.setText("");
                return;
            }
            try {
                double paid = Double.parseDouble(amtStr);
                if (paid < total) {
                    changeLabel.setText("Change: ₱0.00");
                } else {
                    double change = paid - total;
                    changeLabel.setText("Change: ₱" + String.format("%.2f", change));
                }
            } catch (NumberFormatException ex) {
                changeLabel.setText("");
            }
        };
        amountField.textProperty().addListener((obs, old, val) -> updateChange.run());
        paymentMethod.valueProperty().addListener((obs, old, val) -> updateChange.run());
        cart.addListener((ListChangeListener<CartItem>) c -> updateChange.run());
        Button payBtn = new Button("Complete Payment");
        payBtn.setStyle("-fx-background-color: #219150; -fx-text-fill: white; -fx-font-size: 15px; -fx-background-radius: 5;");
        payBtn.setPrefWidth(220);
        // Move payment logic to a method for reuse
        Runnable handlePayment = () -> {
            double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
            String amtStr = amountField.getText();
            errorLabel.setText("");
            if (amtStr.isEmpty()) {
                errorLabel.setText("Please enter amount paid.");
                return;
            }
            try {
                double paid = Double.parseDouble(amtStr);
                double subtotal = total;
                double discount = 0.0;
                double tax = 0.0;
                if (paymentMethod.getValue().equals("Cash")) {
                    if (paid < total) {
                        errorLabel.setText("Insufficient cash.");
                    } else {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Confirm Payment");
                        confirm.setHeaderText(null);
                        confirm.setContentText("Are you sure you want to complete this payment?");
                        confirm.initModality(Modality.APPLICATION_MODAL);
                        confirm.showAndWait().ifPresent(type -> {
                            if (type == ButtonType.OK) {
                                try {
                                    String receiptNumber = PosTransactionDAO.generateNextInvoiceNo();
                                    int posTransactionId = PosTransactionDAO.insertPosTransaction(
                                        receiptNumber,
                                        new Timestamp(System.currentTimeMillis()),
                                        paymentMethod.getValue(),
                                        staffId,
                                        subtotal,
                                        discount,
                                        tax,
                                        total,
                                        paid
                                    );
                                    // Prepare items for physical_sale_items
                                    List<Map<String, Object>> items = new ArrayList<>();
                                    for (CartItem item : cart) {
                                        Map<String, Object> row = new HashMap<>();
                                        row.put("sku", item.getProduct().getSku());
                                        row.put("order_quantity", item.getQuantity());
                                        row.put("stock_quantity", item.getProduct().getQuantity());
                                        row.put("subtotal", item.getSubtotal());
                                        Integer onlineInventoryItemId = null;
                                        Integer inStoreInventoryItemId = null;
                                        String saleChannel = "in-store";
                                        try {
                                            ProductDAO.InventoryItemInfo info = ProductDAO.getInventoryItemInfoBySku(item.getProduct().getSku());
                                            if (info != null) {
                                                saleChannel = info.saleChannel;
                                                if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
                                                    onlineInventoryItemId = info.inventoryItemId;
                                                } else {
                                                    inStoreInventoryItemId = info.inventoryItemId;
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        row.put("sale_channel", saleChannel);
                                        row.put("online_inventory_item_id", onlineInventoryItemId);
                                        row.put("in_store_inventory_item_id", inStoreInventoryItemId);
                                        items.add(row);
                                    }
                                    PosTransactionDAO.insertPhysicalSaleItems(posTransactionId, items);
                                    // Decrease product quantities in the database efficiently using batch update
                                    Map<String, Integer> inStoreMap = new java.util.HashMap<>();
                                    Map<String, Integer> onlineMap = new java.util.HashMap<>();
                                    for (CartItem item : cart) {
                                        String sku = item.getProduct().getSku();
                                        int qty = item.getQuantity();
                                        String saleChannel = "in-store";
                                        try {
                                            ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(sku);
                                            if (info != null) {
                                                saleChannel = info.saleChannel;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
                                            onlineMap.put(sku, qty);
                                        } else {
                                            inStoreMap.put(sku, qty);
                                        }
                                    }
                                    try {
                                        if (!inStoreMap.isEmpty()) ProductDAO.decreaseProductQuantitiesBatch(inStoreMap, "in-store");
                                        if (!onlineMap.isEmpty()) ProductDAO.decreaseProductQuantitiesBatch(onlineMap, "both");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {
                                        onPaymentCompleted.run();
                                        cart.clear();
                                        amountField.clear();
                                        changeLabel.setText("");
                                    }, cashierName, receiptNumber);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    errorLabel.setText("Error processing transaction.");
                                }
                            }
                        });
                    }
                } else {
                    if (paid < total) {
                        errorLabel.setText("Insufficient amount.");
                    } else {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Confirm Payment");
                        confirm.setHeaderText(null);
                        confirm.setContentText("Are you sure you want to complete this payment?");
                        confirm.initModality(Modality.APPLICATION_MODAL);
                        confirm.showAndWait().ifPresent(type -> {
                            if (type == ButtonType.OK) {
                                try {
                                    String receiptNumber = PosTransactionDAO.generateNextInvoiceNo();
                                    PosTransactionDAO.insertPosTransaction(
                                        receiptNumber,
                                        new Timestamp(System.currentTimeMillis()),
                                        paymentMethod.getValue(),
                                        staffId,
                                        subtotal,
                                        discount,
                                        tax,
                                        total,
                                        paid
                                    );
                                    // Decrease product quantities in the database efficiently using batch update
                                    Map<String, Integer> inStoreMap2 = new java.util.HashMap<>();
                                    Map<String, Integer> onlineMap2 = new java.util.HashMap<>();
                                    for (CartItem item : cart) {
                                        String sku = item.getProduct().getSku();
                                        int qty = item.getQuantity();
                                        String saleChannel = "in-store";
                                        try {
                                            ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(sku);
                                            if (info != null) {
                                                saleChannel = info.saleChannel;
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
                                            onlineMap2.put(sku, qty);
                                        } else {
                                            inStoreMap2.put(sku, qty);
                                        }
                                    }
                                    try {
                                        if (!inStoreMap2.isEmpty()) ProductDAO.decreaseProductQuantitiesBatch(inStoreMap2, "in-store");
                                        if (!onlineMap2.isEmpty()) ProductDAO.decreaseProductQuantitiesBatch(onlineMap2, "both");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {
                                        onPaymentCompleted.run();
                                        cart.clear();
                                        amountField.clear();
                                        changeLabel.setText("");
                                    }, cashierName, receiptNumber);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    errorLabel.setText("Error processing transaction.");
                                }
                            }
                        });
                    }
                }
            } catch (NumberFormatException ex) {
                errorLabel.setText("Invalid amount.");
            }
        };
        payBtn.setOnAction(e -> handlePayment.run());
        amountField.setOnAction(e -> handlePayment.run());
        VBox.setMargin(paymentMethod, new Insets(0, 0, 10, 0));
        VBox.setMargin(amountField, new Insets(0, 0, 15, 0));
        VBox.setMargin(payBtn, new Insets(0, 0, 10, 0));
        VBox.setMargin(errorLabel, new Insets(0, 0, 10, 0));
        getChildren().addAll(paymentLabel, paymentMethodLabel, paymentMethod, amountPaidLabel, amountField, payBtn, errorLabel, summaryBox);
        setSpacing(0);
    }

    private void setupCartListeners(Label subtotalSummary, Label totalSummary, ObservableList<CartItem> cart) {
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