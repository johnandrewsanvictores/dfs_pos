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
import javafx.application.Platform;
import java.util.concurrent.CountDownLatch;
import javafx.concurrent.Task;

public class PaymentSectionView extends VBox {
    private final int staffId;
    private final String cashierName;
    private final StackPane overlay = new StackPane();
    private final ProgressIndicator loader = new ProgressIndicator();
    private final VBox paymentContent = new VBox();
    private final TextField refNoField = new TextField();
    private final VBox refNoBox = new VBox();

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
        // Add listeners to each CartItem's quantityProperty to update change when quantity changes
        for (CartItem item : cart) {
            item.quantityProperty().addListener((obs, oldVal, newVal) -> updateChange.run());
        }
        cart.addListener((ListChangeListener<CartItem>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (CartItem item : c.getAddedSubList()) {
                        item.quantityProperty().addListener((obs, oldVal, newVal) -> updateChange.run());
                    }
                }
            }
        });
        Button payBtn = new Button("Complete Payment");
        payBtn.setStyle("-fx-background-color: #219150; -fx-text-fill: white; -fx-font-size: 15px; -fx-background-radius: 5;");
        payBtn.setPrefWidth(220);
        Label refNoLabel = new Label("Reference Number");
        refNoField.setPromptText("Enter E-Wallet Reference Number");
        refNoBox.getChildren().setAll(refNoLabel, refNoField);
        refNoBox.setVisible(false);
        refNoBox.setManaged(false);
        // Show/hide reference number field based on payment method
        paymentMethod.valueProperty().addListener((obs, old, val) -> {
            boolean isEwallet = "E-Wallet".equals(val);
            refNoBox.setVisible(isEwallet);
            refNoBox.setManaged(isEwallet);
            if (isEwallet && !paymentContent.getChildren().contains(refNoBox)) {
                int idx = paymentContent.getChildren().indexOf(amountField) + 1;
                paymentContent.getChildren().add(idx, refNoBox);
            } else if (!isEwallet && paymentContent.getChildren().contains(refNoBox)) {
                paymentContent.getChildren().remove(refNoBox);
            }
        });
        Runnable handlePayment = () -> {
            double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
            String amtStr = amountField.getText();
            errorLabel.setText("");
            boolean isEwallet = "E-Wallet".equals(paymentMethod.getValue());
            String refNo = isEwallet ? refNoField.getText().trim() : null;
            if (amtStr.isEmpty()) {
                errorLabel.setText("Please enter amount paid.");
                return;
            }
            if (isEwallet && refNo == null || refNo.isEmpty()) {
                errorLabel.setText("Reference number is required for E-Wallet payments.");
                return;
            }
            try {
                double paid = Double.parseDouble(amtStr);
                double subtotal = total;
                double discount = 0.0;
                double tax = 0.0;
                if (cart.isEmpty()) {
                    Platform.runLater(() -> {
                        errorLabel.setText("Cart is empty. Please add items to the cart.");
                        overlay.setVisible(false);
                        payBtn.setDisable(false);
                    });
                    return;
                }
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
                                java.sql.Connection conn = null;
                                try {
                                    conn = pos.db.DBConnection.getConnection();
                                    conn.setAutoCommit(false);
                                    long startTime = System.currentTimeMillis();
                                    long t1 = startTime;
                                    System.out.println("[Timing] Start payment process");
                                    String receiptNumber = PosTransactionDAO.generateNextInvoiceNo(conn);
                                    long t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Invoice generation: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    int posTransactionId = PosTransactionDAO.insertPosTransaction(
                                        conn,
                                        receiptNumber,
                                        new Timestamp(System.currentTimeMillis()),
                                        paymentMethod.getValue(),
                                        staffId,
                                        subtotal,
                                        discount,
                                        tax,
                                        total,
                                        paid,
                                        isEwallet ? refNo : null
                                    );
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Transaction insertion: " + (t2 - t1) + " ms");
                                    t1 = t2;
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
                                            ProductDAO.InventoryItemInfo info = ProductDAO.getInventoryItemInfoBySku(conn, item.getProduct().getSku());
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
                                    PosTransactionDAO.insertPhysicalSaleItems(conn, posTransactionId, items);
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Sale items batch insertion: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    // Decrease product quantities in the database efficiently using batch update
                                    Map<String, Integer> inStoreMap = new java.util.HashMap<>();
                                    Map<String, Integer> onlineMap = new java.util.HashMap<>();
                                    for (CartItem item : cart) {
                                        String sku = item.getProduct().getSku();
                                        int qty = item.getQuantity();
                                        String saleChannel = "in-store";
                                        try {
                                            ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(conn, sku);
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
                                        if (!inStoreMap.isEmpty()) ProductDAO.batchUpdateInventory(conn, inStoreMap, "in-store");
                                        if (!onlineMap.isEmpty()) ProductDAO.batchUpdateInventory(conn, onlineMap, "both");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Inventory batch update: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    conn.commit();
                                    ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {
                                        onPaymentCompleted.run();
                                        cart.clear();
                                        amountField.clear();
                                        changeLabel.setText("");
                                        paymentMethod.setValue("Cash");
                                        refNoField.clear();
                                        refNoBox.setVisible(false);
                                        refNoBox.setManaged(false);
                                    }, cashierName, receiptNumber);
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Receipt generation: " + (t2 - t1) + " ms");
                                    System.out.println("[Timing] Total payment process: " + (t2 - startTime) + " ms");
                                } catch (Exception ex) {
                                    if (conn != null) {
                                        try { conn.rollback(); } catch (Exception ignore) {}
                                    }
                                    ex.printStackTrace();
                                    errorLabel.setText("Error processing transaction.");
                                } finally {
                                    if (conn != null) try { conn.close(); } catch (Exception ignore) {}
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
                                java.sql.Connection conn = null;
                                try {
                                    conn = pos.db.DBConnection.getConnection();
                                    conn.setAutoCommit(false);
                                    long startTime = System.currentTimeMillis();
                                    long t1 = startTime;
                                    System.out.println("[Timing] Start payment process");
                                    String receiptNumber = PosTransactionDAO.generateNextInvoiceNo(conn);
                                    long t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Invoice generation: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    int posTransactionId = PosTransactionDAO.insertPosTransaction(
                                        conn,
                                        receiptNumber,
                                        new Timestamp(System.currentTimeMillis()),
                                        paymentMethod.getValue(),
                                        staffId,
                                        subtotal,
                                        discount,
                                        tax,
                                        total,
                                        paid,
                                        isEwallet ? refNo : null
                                    );
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Transaction insertion: " + (t2 - t1) + " ms");
                                    t1 = t2;
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
                                            ProductDAO.InventoryItemInfo info = ProductDAO.getInventoryItemInfoBySku(conn, item.getProduct().getSku());
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
                                    PosTransactionDAO.insertPhysicalSaleItems(conn, posTransactionId, items);
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Sale items batch insertion: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    // Decrease product quantities in the database efficiently using batch update
                                    Map<String, Integer> inStoreMap2 = new java.util.HashMap<>();
                                    Map<String, Integer> onlineMap2 = new java.util.HashMap<>();
                                    for (CartItem item : cart) {
                                        String sku = item.getProduct().getSku();
                                        int qty = item.getQuantity();
                                        String saleChannel = "in-store";
                                        try {
                                            ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(conn, sku);
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
                                        if (!inStoreMap2.isEmpty()) ProductDAO.batchUpdateInventory(conn, inStoreMap2, "in-store");
                                        if (!onlineMap2.isEmpty()) ProductDAO.batchUpdateInventory(conn, onlineMap2, "both");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Inventory batch update: " + (t2 - t1) + " ms");
                                    t1 = t2;
                                    conn.commit();
                                    ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {
                                        onPaymentCompleted.run();
                                        cart.clear();
                                        amountField.clear();
                                        changeLabel.setText("");
                                        paymentMethod.setValue("Cash");
                                        refNoField.clear();
                                        refNoBox.setVisible(false);
                                        refNoBox.setManaged(false);
                                    }, cashierName, receiptNumber);
                                    t2 = System.currentTimeMillis();
                                    System.out.println("[Timing] Receipt generation: " + (t2 - t1) + " ms");
                                    System.out.println("[Timing] Total payment process: " + (t2 - startTime) + " ms");
                                } catch (Exception ex) {
                                    if (conn != null) {
                                        try { conn.rollback(); } catch (Exception ignore) {}
                                    }
                                    ex.printStackTrace();
                                    errorLabel.setText("Error processing transaction.");
                                } finally {
                                    if (conn != null) try { conn.close(); } catch (Exception ignore) {}
                                }
                            }
                        });
                    }
                }
            } catch (NumberFormatException ex) {
                errorLabel.setText("Invalid amount.");
            }
        };
        payBtn.setOnAction(e -> runPaymentTask(cart, products, onPaymentCompleted, staffId, cashierName, paymentMethod, amountField, errorLabel, changeLabel, payBtn));
        amountField.setOnAction(e -> runPaymentTask(cart, products, onPaymentCompleted, staffId, cashierName, paymentMethod, amountField, errorLabel, changeLabel, payBtn));
        VBox.setMargin(paymentMethod, new Insets(0, 0, 10, 0));
        VBox.setMargin(amountField, new Insets(0, 0, 15, 0));
        VBox.setMargin(payBtn, new Insets(0, 0, 10, 0));
        VBox.setMargin(errorLabel, new Insets(0, 0, 10, 0));
        // Create the main VBox for payment content
        paymentContent.getChildren().addAll(paymentLabel, paymentMethodLabel, paymentMethod, amountPaidLabel, amountField, payBtn, errorLabel, summaryBox);
        paymentContent.setSpacing(10);
        // Style the loader and overlay
        loader.setMaxSize(80, 80);
        loader.setStyle("-fx-progress-color: #b48c5f;");
        overlay.getChildren().add(loader);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-alignment: center;");
        overlay.setVisible(false);
        // Use a StackPane as the root
        StackPane root = new StackPane(paymentContent, overlay);
        getChildren().add(root);
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

    private void runPaymentTask(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted, int staffId, String cashierName, ComboBox<String> paymentMethod, TextField amountField, Label errorLabel, Label changeLabel, Button payBtn) {
        payBtn.setDisable(true);
        Task<Void> paymentTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
                String amtStr = amountField.getText();
                Platform.runLater(() -> errorLabel.setText(""));
                boolean isEwallet = "E-Wallet".equals(paymentMethod.getValue());
                String refNo = isEwallet ? refNoField.getText().trim() : null;
                if (amtStr.isEmpty()) {
                    Platform.runLater(() -> {
                        errorLabel.setText("Please enter amount paid.");
                        overlay.setVisible(false);
                        payBtn.setDisable(false);
                    });
                    return null;
                }
                if (isEwallet && (refNo == null || refNo.isEmpty())) {
                    Platform.runLater(() -> {
                        errorLabel.setText("Reference number is required for E-Wallet payments.");
                        overlay.setVisible(false);
                        payBtn.setDisable(false);
                    });
                    return null;
                }
                try {
                    double paid = Double.parseDouble(amtStr);
                    double subtotal = total;
                    double discount = 0.0;
                    double tax = 0.0;
                    if (cart.isEmpty()) {
                        Platform.runLater(() -> {
                            errorLabel.setText("Cart is empty. Please add items to the cart.");
                            overlay.setVisible(false);
                            payBtn.setDisable(false);
                        });
                        return null;
                    }
                    if (paymentMethod.getValue().equals("Cash")) {
                        if (paid < total) {
                            Platform.runLater(() -> {
                                errorLabel.setText("Insufficient cash.");
                                overlay.setVisible(false);
                                payBtn.setDisable(false);
                            });
                            return null;
                        }
                    } else {
                        if (paid < total) {
                            Platform.runLater(() -> {
                                errorLabel.setText("Insufficient amount.");
                                overlay.setVisible(false);
                                payBtn.setDisable(false);
                            });
                            return null;
                        }
                    }
                    // Show confirmation dialog on UI thread and wait for result
                    final boolean[] confirmed = {false};
                    CountDownLatch latch = new CountDownLatch(1);
                    Platform.runLater(() -> {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Confirm Payment");
                        confirm.setHeaderText(null);
                        confirm.setContentText("Are you sure you want to complete this payment?");
                        confirm.initModality(Modality.APPLICATION_MODAL);
                        confirm.showAndWait().ifPresent(type -> {
                            confirmed[0] = (type == ButtonType.OK);
                            latch.countDown();
                        });
                    });
                    latch.await();
                    if (!confirmed[0]) {
                        Platform.runLater(() -> {
                            overlay.setVisible(false);
                            payBtn.setDisable(false);
                        });
                        return null;
                    }
                    // Only show loader after confirmation
                    Platform.runLater(() -> overlay.setVisible(true));
                    // Payment logic (copied from original, with Platform.runLater for UI updates)
                    java.sql.Connection conn = null;
                    try {
                        conn = pos.db.DBConnection.getConnection();
                        conn.setAutoCommit(false);
                        long startTime = System.currentTimeMillis();
                        long t1 = startTime;
                        System.out.println("[Timing] Start payment process");
                        String receiptNumber = PosTransactionDAO.generateNextInvoiceNo(conn);
                        long t2 = System.currentTimeMillis();
                        System.out.println("[Timing] Invoice generation: " + (t2 - t1) + " ms");
                        t1 = t2;
                        int posTransactionId = PosTransactionDAO.insertPosTransaction(
                            conn,
                            receiptNumber,
                            new Timestamp(System.currentTimeMillis()),
                            paymentMethod.getValue(),
                            staffId,
                            subtotal,
                            discount,
                            tax,
                            total,
                            paid,
                            isEwallet ? refNo : null
                        );
                        t2 = System.currentTimeMillis();
                        System.out.println("[Timing] Transaction insertion: " + (t2 - t1) + " ms");
                        t1 = t2;
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
                                ProductDAO.InventoryItemInfo info = ProductDAO.getInventoryItemInfoBySku(conn, item.getProduct().getSku());
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
                        PosTransactionDAO.insertPhysicalSaleItems(conn, posTransactionId, items);
                        t2 = System.currentTimeMillis();
                        System.out.println("[Timing] Sale items batch insertion: " + (t2 - t1) + " ms");
                        t1 = t2;
                        // Decrease product quantities in the database efficiently using batch update
                        Map<String, Integer> inStoreMap = new java.util.HashMap<>();
                        Map<String, Integer> onlineMap = new java.util.HashMap<>();
                        for (CartItem item : cart) {
                            String sku = item.getProduct().getSku();
                            int qty = item.getQuantity();
                            String saleChannel = "in-store";
                            try {
                                ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(conn, sku);
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
                            if (!inStoreMap.isEmpty()) ProductDAO.batchUpdateInventory(conn, inStoreMap, "in-store");
                            if (!onlineMap.isEmpty()) ProductDAO.batchUpdateInventory(conn, onlineMap, "both");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        t2 = System.currentTimeMillis();
                        System.out.println("[Timing] Inventory batch update: " + (t2 - t1) + " ms");
                        t1 = t2;
                        conn.commit();
                        Platform.runLater(() -> {
                            ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {
                                onPaymentCompleted.run();
                                cart.clear();
                                amountField.clear();
                                changeLabel.setText("");
                                paymentMethod.setValue("Cash");
                                refNoField.clear();
                                refNoBox.setVisible(false);
                                refNoBox.setManaged(false);
                            }, cashierName, receiptNumber);
                        });
                        t2 = System.currentTimeMillis();
                        System.out.println("[Timing] Receipt generation: " + (t2 - t1) + " ms");
                        System.out.println("[Timing] Total payment process: " + (t2 - startTime) + " ms");
                    } catch (Exception ex) {
                        if (conn != null) {
                            try { conn.rollback(); } catch (Exception ignore) {}
                        }
                        ex.printStackTrace();
                        Platform.runLater(() -> errorLabel.setText("Error processing transaction."));
                    } finally {
                        if (conn != null) try { conn.close(); } catch (Exception ignore) {}
                    }
                } catch (NumberFormatException ex) {
                    Platform.runLater(() -> errorLabel.setText("Invalid amount."));
                }
                Platform.runLater(() -> {
                    overlay.setVisible(false);
                    payBtn.setDisable(false);
                });
                return null;
            }
        };
        new Thread(paymentTask).start();
    }
} 