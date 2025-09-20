package pos.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import pos.model.ReturnItem;
import pos.db.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReturnsManager {
    
    private ObservableList<ReturnItem> returnItems = FXCollections.observableArrayList();
    private double originalSubtotal = 0.0;
    private double originalDiscount = 0.0;
    private double originalTax = 0.0;
    private String invoiceNumber = "";
    
    // Returns summary data
    public static class ReturnsSummary {
        public final double originalSubtotal;
        public final double discountApplied;
        public final double tax;
        public final double refundItems;
        public final double refundTotal;
        
        public ReturnsSummary(double originalSubtotal, double discountApplied, double tax, double refundItems, double refundTotal) {
            this.originalSubtotal = originalSubtotal;
            this.discountApplied = discountApplied;
            this.tax = tax;
            this.refundItems = refundItems;
            this.refundTotal = refundTotal;
        }
    }
    
    public ReturnsManager() {
        // Initialize with empty data
    }
    
    public boolean loadTransaction(String invoiceNo) {
        try {
            this.invoiceNumber = invoiceNo;
            return fetchTransactionData(invoiceNo);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean fetchTransactionData(String invoiceNo) {
        String query = "SELECT " +
                "psi.id as invoice_item_id, " +
                "psi.sku, " +
                "psi.subtotal, " +
                "psi.order_quantity as quantity, " +
                "psi.online_inventory_item_id, " +
                "psi.in_store_inventory_item_id, " +
                "(psi.subtotal / psi.order_quantity) as unit_price, " +
                "pt.subtotal as transaction_subtotal, " +
                "pt.discount as transaction_discount, " +
                "pt.tax as transaction_tax " +
            "FROM pos_transactions pt " +
            "JOIN physical_sale_items psi ON pt.id = psi.pos_transaction_id " +
            "WHERE pt.invoice_no = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, invoiceNo);
            ResultSet rs = stmt.executeQuery();
            
            List<ReturnItem> items = new ArrayList<>();
            boolean hasData = false;
            
            // Process each item
            while (rs.next()) {
                if (!hasData) {
                    // Set transaction totals from first row
                    originalSubtotal = rs.getDouble("transaction_subtotal");
                    originalDiscount = rs.getDouble("transaction_discount");
                    originalTax = rs.getDouble("transaction_tax");
                    hasData = true;
                }
                
                double itemSubtotal = rs.getDouble("subtotal");
                
                String sku = rs.getString("sku");
                String productName = sku; // Use SKU as name for now
                
                // Calculate proportional discount for this item
                double itemDiscount = 0.0;
                if (originalDiscount > 0 && originalSubtotal > 0) {
                    itemDiscount = (itemSubtotal / originalSubtotal) * originalDiscount;
                }
                
                // Get database IDs
                int invoiceItemId = rs.getInt("invoice_item_id");
                int onlineInventoryItemId = rs.getInt("online_inventory_item_id");
                int inStoreInventoryId = rs.getInt("in_store_inventory_item_id");
                
                ReturnItem item = new ReturnItem(
                    productName,
                    sku,
                    rs.getDouble("unit_price"),
                    rs.getInt("quantity"),
                    itemDiscount / rs.getInt("quantity"), // Per unit discount
                    invoiceItemId,
                    onlineInventoryItemId,
                    inStoreInventoryId
                );
                items.add(item);
            }
            
            if (hasData) {
                returnItems.setAll(items);
                return true;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return false;
    }
    
    public ObservableList<ReturnItem> getReturnItems() {
        return returnItems;
    }
    
    public ReturnsSummary calculateReturnsSummary() {
        double refundItems = returnItems.stream()
            .mapToDouble(ReturnItem::getRefundAmount)
            .sum();
        
        double refundTotal = refundItems;
        
        return new ReturnsSummary(
            originalSubtotal,
            originalDiscount,
            originalTax,
            refundItems,
            refundTotal
        );
    }
    
    public TableView<ReturnItem> createReturnsTableView() {
        TableView<ReturnItem> table = new TableView<>();
        table.setItems(returnItems);
        table.getStyleClass().add("returns-table");
        
        // Apply clean styling with light background
        table.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; -fx-border-width: 1px; -fx-border-radius: 5px;");
        // Use constrained resize policy to fill all available width
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        // Product column - wider for better readability (25% of width)
        TableColumn<ReturnItem, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(cellData -> cellData.getValue().productNameProperty());
        productCol.setMinWidth(100);
        productCol.setPrefWidth(200);
        
        // Price column (12% of width)
        TableColumn<ReturnItem, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().priceProperty().asObject());
        priceCol.setCellFactory(col -> new TableCell<ReturnItem, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText("");
                } else {
                    setText("₱" + String.format("%.2f", price));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        priceCol.setMinWidth(70);
        priceCol.setPrefWidth(90);
        
        // Qty Purchased column (15% of width)
        TableColumn<ReturnItem, Integer> qtyPurchasedCol = new TableColumn<>("Qty Purchased");
        qtyPurchasedCol.setCellValueFactory(cellData -> cellData.getValue().qtyPurchasedProperty().asObject());
        qtyPurchasedCol.setCellFactory(col -> new TableCell<ReturnItem, Integer>() {
            @Override
            protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                if (empty || qty == null) {
                    setText("");
                } else {
                    setText(qty.toString());
                }
                setStyle("-fx-alignment: CENTER;");
            }
        });
        qtyPurchasedCol.setMinWidth(80);
        qtyPurchasedCol.setPrefWidth(100);
        
        // Discount column (12% of width)
        TableColumn<ReturnItem, Double> discountCol = new TableColumn<>("Discount");
        discountCol.setCellValueFactory(cellData -> cellData.getValue().discountProperty().asObject());
        discountCol.setCellFactory(col -> new TableCell<ReturnItem, Double>() {
            @Override
            protected void updateItem(Double discount, boolean empty) {
                super.updateItem(discount, empty);
                if (empty || discount == null) {
                    setText("");
                } else {
                    setText("₱" + String.format("%.2f", discount));
                }
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        discountCol.setMinWidth(70);
        discountCol.setPrefWidth(85);
        
        // Qty to Return column (15% of width)
        TableColumn<ReturnItem, Integer> qtyToReturnCol = new TableColumn<>("Qty to Return");
        qtyToReturnCol.setCellValueFactory(cellData -> cellData.getValue().qtyToReturnProperty().asObject());
        qtyToReturnCol.setCellFactory(col -> new TableCell<ReturnItem, Integer>() {
            @Override
            protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                if (empty || qty == null) {
                    setText("");
                } else {
                    setText(qty.toString());
                }
                setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: #1976d2;");
            }
        });
        qtyToReturnCol.setMinWidth(80);
        qtyToReturnCol.setPrefWidth(100);
        
        // Refund Amount column (15% of width)
        TableColumn<ReturnItem, Double> refundCol = new TableColumn<>("Refund Amount");
        refundCol.setCellValueFactory(cellData -> cellData.getValue().refundAmountProperty().asObject());
        refundCol.setCellFactory(col -> new TableCell<ReturnItem, Double>() {
            @Override
            protected void updateItem(Double refund, boolean empty) {
                super.updateItem(refund, empty);
                if (empty || refund == null) {
                    setText("");
                } else {
                    setText("₱" + String.format("%.2f", refund));
                    if (refund > 0) {
                        setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold; -fx-alignment: CENTER-RIGHT;");
                    } else {
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                }
            }
        });
        refundCol.setMinWidth(90);
        refundCol.setPrefWidth(110);
        
        // Action column (6% of width, will expand with flex policy)
        TableColumn<ReturnItem, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(col -> new TableCell<ReturnItem, Void>() {
            private final Button plusBtn = new Button("+");
            private final Button minusBtn = new Button("-");
            private final HBox actionBox = new HBox(3, minusBtn, plusBtn);
            
            {
                // Style buttons to be more compact and cleaner
                plusBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 6; -fx-border-radius: 3; -fx-background-radius: 3;");
                minusBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 6; -fx-border-radius: 3; -fx-background-radius: 3;");
                
                plusBtn.setMinWidth(25);
                plusBtn.setMaxWidth(25);
                minusBtn.setMinWidth(25);
                minusBtn.setMaxWidth(25);
                
                actionBox.setAlignment(Pos.CENTER);
                
                // Button actions
                plusBtn.setOnAction(e -> {
                    ReturnItem item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        item.incrementQtyToReturn();
                    }
                });
                
                minusBtn.setOnAction(e -> {
                    ReturnItem item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        item.decrementQtyToReturn();
                    }
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionBox);
                }
            }
        });
        actionCol.setMinWidth(60);
        actionCol.setPrefWidth(70);
        actionCol.setSortable(false);
        actionCol.setResizable(false);
        
        table.getColumns().add(productCol);
        table.getColumns().add(priceCol);
        table.getColumns().add(qtyPurchasedCol);
        table.getColumns().add(discountCol);
        table.getColumns().add(qtyToReturnCol);
        table.getColumns().add(refundCol);
        table.getColumns().add(actionCol);
        
        return table;
    }
    
    public VBox createReturnsSummaryBox() {
        VBox summaryBox = new VBox(5);
        summaryBox.getStyleClass().add("section");
        
        Label originalSubtotalLabel = new Label();
        Label discountAppliedLabel = new Label();
        Label taxLabel = new Label();
        Label refundItemsLabel = new Label();
        Label refundTotalLabel = new Label();
        
        // Style labels
        refundItemsLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
        refundTotalLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold; -fx-font-size: 16px;");
        
        // Update summary when return items change
        Runnable updateSummary = () -> {
            ReturnsSummary summary = calculateReturnsSummary();
            originalSubtotalLabel.setText("Original Subtotal: ₱" + String.format("%.2f", summary.originalSubtotal));
            discountAppliedLabel.setText("Discount Applied: -₱" + String.format("%.2f", summary.discountApplied));
            taxLabel.setText("Tax: +₱" + String.format("%.2f", summary.tax));
            refundItemsLabel.setText("Refund Items: -₱" + String.format("%.2f", summary.refundItems));
            refundTotalLabel.setText("Refund Total: ₱" + String.format("%.2f", summary.refundTotal));
        };
        
        // Listen for changes in return items
        returnItems.addListener((javafx.collections.ListChangeListener<ReturnItem>) c -> updateSummary.run());
        
        // Listen for changes in individual return item quantities
        for (ReturnItem item : returnItems) {
            item.qtyToReturnProperty().addListener((obs, oldVal, newVal) -> updateSummary.run());
        }
        
        // Add listener for future items
        returnItems.addListener((javafx.collections.ListChangeListener<ReturnItem>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (ReturnItem item : c.getAddedSubList()) {
                        item.qtyToReturnProperty().addListener((obs, oldVal, newVal) -> updateSummary.run());
                    }
                }
            }
        });
        
        updateSummary.run(); // Initial update
        
        summaryBox.getChildren().addAll(originalSubtotalLabel, discountAppliedLabel, taxLabel, refundItemsLabel, refundTotalLabel);
        
        return summaryBox;
    }
    
    public String getInvoiceNumber() {
        return invoiceNumber;
    }
    
    public void clearReturns() {
        returnItems.clear();
        originalSubtotal = 0.0;
        originalDiscount = 0.0;
        originalTax = 0.0;
        invoiceNumber = "";
    }
}