package pos.view;

import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.geometry.Pos;
import pos.model.CartItem;
import pos.model.Product;
import pos.model.ReturnItem;
import javafx.geometry.Insets;
import javafx.collections.ListChangeListener;
import javafx.stage.Modality;
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
import pos.db.PromotionDao;
import javafx.animation.Timeline;
import pos.db.SystemSettingsDAO;

public class PaymentSectionView extends VBox {
    
    // Constants
    private static final int DEFAULT_PADDING = 10;
    private static final int PREFERRED_WIDTH = 300;
    private static final int LOADER_SIZE = 80;
    private static final int CHANGE_LABEL_FONT_SIZE = 9;
    private static final int SUMMARY_FONT_SIZE = 14;
    private static final int TOTAL_FONT_SIZE = 16;
    private static final int PAY_BUTTON_WIDTH = 220;
    private static final double PROMOTION_REFRESH_MINUTES = 2.0;
    
    // Style constants
    private static final String ERROR_LABEL_STYLE = "-fx-text-fill: red;";
    private static final String CHANGE_LABEL_STYLE = "-fx-text-fill: green;";
    private static final String TOTAL_LABEL_STYLE = "-fx-font-weight: bold;";
    private static final String PAY_BUTTON_STYLE = "-fx-background-color: #219150; -fx-text-fill: white; -fx-font-size: 15px; -fx-background-radius: 5;";
    private static final String LOADER_STYLE = "-fx-progress-color: #b48c5f;";
    private static final String OVERLAY_STYLE = "-fx-background-color: rgba(255,255,255,0.7); -fx-alignment: center;";
    
    // Payment methods
    private static final String PAYMENT_METHOD_CASH = "Cash";
    private static final String PAYMENT_METHOD_EWALLET = "E-Wallet";
    
    // UI spacing
    private static final int COMPONENT_SPACING = 10;
    private static final int SUMMARY_BOX_SPACING = 5;
    
    // Instance fields
    private final int staffId;
    private final String cashierName;
    private final StackPane overlay = new StackPane();
    private final ProgressIndicator loader = new ProgressIndicator();
    private final VBox paymentContent = new VBox();
    private final TextField refNoField = new TextField();
    private final VBox refNoBox = new VBox();
    private List<pos.db.PromotionDao.Promotion> cachedPromotions = null;
    private final Label discountSummary = new Label("Discount: ₱0.00");
    private final Label taxSummary = new Label("Tax: ₱0.00");
    private int cachedVatRate = 0;
    private boolean cachedVatEnabled = false;
    
    // Returns mode fields
    private boolean isReturnsMode = false;
    private ReturnsManager returnsManager = new ReturnsManager();
    private int lastAuthenticatedSupervisorId = -1;
    private Runnable onReturnsModeToggle;
    private VBox normalPaymentSummary;
    private VBox returnsSummary;
    // Label to show the original invoice number in returns mode
    private Label originalInvoiceLabel;
    
    // Payment fields references for hiding in returns mode
    private Label paymentMethodLabel;
    private ComboBox<String> paymentMethodCombo;
    private Label amountPaidLabel;
    private TextField amountPaidField;
    private Label changeLabel; // Add changeLabel as a field
    private Button completePaymentBtn;
    private Button processReturnsButton; // Reference to the returns button

    public PaymentSectionView(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted, int staffId, String cashierName, Label dateLabel, Label timeLabel) {
        this.staffId = staffId;
        this.cashierName = cashierName;
        this.returnsManager = new ReturnsManager();
        
        initializeComponent();
        initializePromotionsAndVAT();

        // Create UI components
        Label paymentLabel = createPaymentLabel();
        processReturnsButton = createProcessReturnsButton(); // Store reference
        paymentMethodCombo = createPaymentMethodCombo();
        amountPaidField = createAmountField();
        Label errorLabel = createErrorLabel();
        changeLabel = createChangeLabel(); // Store in field instead of local variable
        VBox summaryBox = createSummaryBox();
        VBox dateTimeBox = createDateTimeBox(dateLabel, timeLabel);
        completePaymentBtn = createPayButton();

        // Create the original invoice label (hidden by default)
        originalInvoiceLabel = new Label();
        originalInvoiceLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #1976d2; -fx-font-weight: bold;");
        originalInvoiceLabel.setVisible(false);
        originalInvoiceLabel.setManaged(false);

        // Store normal payment summary for later
        normalPaymentSummary = summaryBox;

        setupReferenceNumberField(paymentMethodCombo);
        setupCartListeners(summaryBox, cart, changeLabel);
        setupChangeCalculation(cart, amountPaidField, paymentMethodCombo, changeLabel);
        setupPaymentHandling(cart, products, onPaymentCompleted, paymentMethodCombo, amountPaidField, errorLabel, completePaymentBtn);

        assemblePaymentContent(paymentLabel, processReturnsButton, paymentMethodCombo, amountPaidField, errorLabel, completePaymentBtn, summaryBox, dateTimeBox);

        // Insert the original invoice label after the processReturnsButton
        int idx = paymentContent.getChildren().indexOf(processReturnsButton);
        if (idx >= 0) {
            paymentContent.getChildren().add(idx + 1, originalInvoiceLabel);
        } else {
            paymentContent.getChildren().add(1, originalInvoiceLabel);
        }

        setupOverlayAndLoader();
        setupPeriodicRefresh();
    }
    
    public void setOnReturnsModeToggle(Runnable callback) {
        this.onReturnsModeToggle = callback;
    }

    private void initializeComponent() {
        setPadding(new Insets(DEFAULT_PADDING));
        setPrefWidth(PREFERRED_WIDTH);
        getStyleClass().add("card");
    }

    private void initializePromotionsAndVAT() {
        loadPromotions();
        refreshVatSettings();
    }

    private void loadPromotions() {
        try (java.sql.Connection conn = pos.db.DBConnection.getConnection()) {
            cachedPromotions = pos.db.PromotionDao.getActiveAutomaticDiscounts(conn);
            logLoadedPromotions();
        } catch (Exception e) {
            e.printStackTrace();
            cachedPromotions = new java.util.ArrayList<>();
        }
    }

    private void logLoadedPromotions() {
        System.out.println("Loaded promotions:");
        for (PromotionDao.Promotion p : cachedPromotions) {
            System.out.println(p.title + " | " + p.type + " | " + p.value + " | " + 
                             p.appliesToType + " | " + p.saleChannel + " | " + 
                             p.activationDate + " - " + p.expirationDate);
        }
    }

    private Label createPaymentLabel() {
        Label paymentLabel = new Label("Payment & Summary");
        paymentLabel.getStyleClass().add("section-title");
        VBox.setMargin(paymentLabel, new Insets(0, 0, COMPONENT_SPACING, 0));
        return paymentLabel;
    }

    private ComboBox<String> createPaymentMethodCombo() {
        ComboBox<String> paymentMethod = new ComboBox<>();
        paymentMethod.getItems().setAll(PAYMENT_METHOD_CASH, PAYMENT_METHOD_EWALLET);
        paymentMethod.setValue(PAYMENT_METHOD_CASH);
        VBox.setMargin(paymentMethod, new Insets(0, 0, COMPONENT_SPACING, 0));
        return paymentMethod;
    }

    private TextField createAmountField() {
        TextField amountField = new TextField();
        amountField.setPromptText("");
        VBox.setMargin(amountField, new Insets(0, 0, 15, 0));
        return amountField;
    }

    private Label createErrorLabel() {
        Label errorLabel = new Label();
        errorLabel.setStyle(ERROR_LABEL_STYLE);
        VBox.setMargin(errorLabel, new Insets(0, 0, COMPONENT_SPACING, 0));
        return errorLabel;
    }

    private Label createChangeLabel() {
        Label changeLabel = new Label();
        changeLabel.setFont(new Font(CHANGE_LABEL_FONT_SIZE));
        changeLabel.setStyle(CHANGE_LABEL_STYLE);
        return changeLabel;
    }

    private VBox createDateTimeBox(Label dateLabel, Label timeLabel) {
        VBox dateTimeBox = new VBox(5);
        dateTimeBox.getStyleClass().add("date-time-section");
        
        // Style the labels for the payment section
        dateLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold; -fx-font-size: 14px;");
        timeLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold; -fx-font-size: 18px;");
        
        dateTimeBox.getChildren().addAll(dateLabel, timeLabel);
        VBox.setMargin(dateTimeBox, new Insets(COMPONENT_SPACING, 0, 0, 0));
        return dateTimeBox;
    }

    private VBox createSummaryBox() {
        VBox summaryBox = new VBox(SUMMARY_BOX_SPACING);
        summaryBox.getStyleClass().add("section");
        
        Label subtotalSummary = createSummaryLabel();
        Label totalSummary = createTotalSummaryLabel();
        Label changeSummary = createSummaryLabel();
        
        summaryBox.getChildren().addAll(subtotalSummary, discountSummary, taxSummary, totalSummary, changeSummary);
        return summaryBox;
    }

    private Label createSummaryLabel() {
        Label label = new Label();
        label.getStyleClass().add("payment-summary");
        label.setFont(new Font(SUMMARY_FONT_SIZE));
        return label;
    }

    private Label createTotalSummaryLabel() {
        Label totalLabel = createSummaryLabel();
        totalLabel.setFont(new Font(TOTAL_FONT_SIZE));
        totalLabel.setStyle(TOTAL_LABEL_STYLE);
        return totalLabel;
    }

    private Button createPayButton() {
        Button payBtn = new Button("Complete Payment");
        payBtn.setStyle(PAY_BUTTON_STYLE);
        payBtn.setPrefWidth(PAY_BUTTON_WIDTH);
        VBox.setMargin(payBtn, new Insets(0, 0, COMPONENT_SPACING, 0));
        return payBtn;
    }

    private Button createProcessReturnsButton() {
        Button processReturnsBtn = new Button("Process Returns");
        processReturnsBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 5;");
        processReturnsBtn.setPrefWidth(PAY_BUTTON_WIDTH);
        VBox.setMargin(processReturnsBtn, new Insets(0, 0, COMPONENT_SPACING, 0));
        
        processReturnsBtn.setOnAction(e -> showInvoiceInputDialog());
        
        return processReturnsBtn;
    }

    private void showInvoiceInputDialog() {
        if (isReturnsMode) {
            // Cancel returns mode
            exitReturnsMode();
        } else {
            // Enter returns mode
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Process Returns");
            dialog.setHeaderText("Enter Invoice Number");
            dialog.setContentText("Invoice No:");
            
            // Style the dialog
            dialog.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
            
            // Show dialog and get result
            java.util.Optional<String> result = dialog.showAndWait();
            result.ifPresent(invoiceNo -> {
                if (invoiceNo.trim().isEmpty()) {
                    showAlert("Error", "Please enter a valid invoice number.");
                } else {
                    processReturn(invoiceNo.trim());
                }
            });
        }
    }

    private void processReturn(String invoiceNo) {
        // Try to load the transaction with validation
        ReturnsManager.InvoiceLoadResult result = returnsManager.loadTransaction(invoiceNo);
        
        if (result.success) {
            enterReturnsMode();
        } else {
            showAlert("Error", result.errorMessage);
        }
    }
    
    private void enterReturnsMode() {
        isReturnsMode = true;

        // Show the original invoice label
        String invoiceNo = returnsManager.getInvoiceNumber();
        originalInvoiceLabel.setText("Original Invoice: " + (invoiceNo != null ? invoiceNo : ""));
        originalInvoiceLabel.setVisible(true);
        originalInvoiceLabel.setManaged(true);

        // Update button text and colors first
        updateButtonsForReturnsMode();

        // Hide payment fields
        hidePaymentFields();

        // Update layout to align button with Payment & Summary (after button text is updated)
        updatePaymentContentLayout();

        // Create and show returns summary
        returnsSummary = returnsManager.createReturnsSummaryBox();

        // Replace summary in payment content
        replaceSummaryInPaymentContent(returnsSummary);

        // Notify parent to hide product catalog and show returns cart
        if (onReturnsModeToggle != null) {
            onReturnsModeToggle.run();
        }
    }
    
    private void exitReturnsMode() {
        isReturnsMode = false;

        // Hide the original invoice label
        originalInvoiceLabel.setVisible(false);
        originalInvoiceLabel.setManaged(false);

        // Update button text and colors
        updateButtonsForReturnsMode();

        // Restore normal layout
        restoreNormalLayout();

        // Show payment fields
        showPaymentFields();

        // Clear returns data
        returnsManager.clearReturns();

        // Restore normal payment summary
        replaceSummaryInPaymentContent(normalPaymentSummary);

        // Notify parent to show product catalog and restore normal cart
        if (onReturnsModeToggle != null) {
            onReturnsModeToggle.run();
        }
    }
    
    private void hidePaymentFields() {
        paymentMethodLabel.setVisible(false);
        paymentMethodLabel.setManaged(false);
        paymentMethodCombo.setVisible(false);
        paymentMethodCombo.setManaged(false);
        amountPaidLabel.setVisible(false);
        amountPaidLabel.setManaged(false);
        amountPaidField.setVisible(false);
        amountPaidField.setManaged(false);
    }
    
    private void showPaymentFields() {
        paymentMethodLabel.setVisible(true);
        paymentMethodLabel.setManaged(true);
        paymentMethodCombo.setVisible(true);
        paymentMethodCombo.setManaged(true);
        amountPaidLabel.setVisible(true);
        amountPaidLabel.setManaged(true);
        amountPaidField.setVisible(true);
        amountPaidField.setManaged(true);
    }
    
    private void updateButtonsForReturnsMode() {
        // Update the process returns button directly using reference
        System.out.println("Updating buttons for returns mode. isReturnsMode: " + isReturnsMode);
        
        if (processReturnsButton != null) {
            String newText = isReturnsMode ? "Cancel Returns Mode" : "Process Returns";
            processReturnsButton.setText(newText);
            System.out.println("Updated button text to: " + newText);
            
            // Update color - orange for cancel, red for process
            if (isReturnsMode) {
                processReturnsButton.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 5;");
            } else {
                processReturnsButton.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 5;");
            }
            
            // Make sure button is visible
            processReturnsButton.setVisible(true);
            processReturnsButton.setManaged(true);
        } else {
            System.out.println("Warning: processReturnsButton reference is null!");
        }
        
        // Update the complete payment button
        if (isReturnsMode) {
            completePaymentBtn.setText("Complete Refund");
            completePaymentBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 15px; -fx-background-radius: 5;");
        } else {
            completePaymentBtn.setText("Complete Payment");
            completePaymentBtn.setStyle(PAY_BUTTON_STYLE);
        }
    }
    
    private void replaceSummaryInPaymentContent(VBox newSummary) {
        // Find and replace the summary box in payment content
        for (int i = 0; i < paymentContent.getChildren().size(); i++) {
            if (paymentContent.getChildren().get(i) instanceof VBox) {
                VBox vbox = (VBox) paymentContent.getChildren().get(i);
                // Check if this is a summary box by looking for summary labels
                if (!vbox.getChildren().isEmpty() && 
                    vbox.getChildren().get(0) instanceof Label) {
                    Label firstLabel = (Label) vbox.getChildren().get(0);
                    if (firstLabel.getText().contains("Subtotal") || 
                        firstLabel.getText().contains("Original Subtotal")) {
                        paymentContent.getChildren().set(i, newSummary);
                        break;
                    }
                }
            }
        }
    }
    
    public boolean isInReturnsMode() {
        return isReturnsMode;
    }
    
    public TableView<?> getReturnsTable() {
        return isReturnsMode ? returnsManager.createReturnsTableView() : null;
    }

    // Method to handle barcode scans in returns mode
    public ReturnsManager.ScanResult handleReturnsBarcodeScanned(String barcode) {
        if (!isReturnsMode || returnsManager == null) {
            return ReturnsManager.ScanResult.INVALID_BARCODE;
        }
        
        return returnsManager.handleBarcodeScanned(barcode);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
        alert.showAndWait();
    }

    private void setupReferenceNumberField(ComboBox<String> paymentMethod) {
        Label refNoLabel = new Label("Reference Number");
        refNoField.setPromptText("Enter E-Wallet Reference Number");
        refNoBox.getChildren().setAll(refNoLabel, refNoField);
        refNoBox.setVisible(false);
        refNoBox.setManaged(false);
        
        paymentMethod.valueProperty().addListener((obs, old, val) -> 
            toggleReferenceNumberField(val));
    }

    private void toggleReferenceNumberField(String paymentMethodValue) {
        boolean isEwallet = PAYMENT_METHOD_EWALLET.equals(paymentMethodValue);
        refNoBox.setVisible(isEwallet);
        refNoBox.setManaged(isEwallet);
        
        if (isEwallet && !paymentContent.getChildren().contains(refNoBox)) {
            int idx = findAmountFieldIndex() + 1;
            paymentContent.getChildren().add(idx, refNoBox);
        } else if (!isEwallet && paymentContent.getChildren().contains(refNoBox)) {
            paymentContent.getChildren().remove(refNoBox);
        }
    }

    private int findAmountFieldIndex() {
        for (int i = 0; i < paymentContent.getChildren().size(); i++) {
            if (paymentContent.getChildren().get(i) instanceof TextField) {
                return i;
            }
        }
        return 0;
    }

    private void setupChangeCalculation(ObservableList<CartItem> cart, TextField amountField, 
                                      ComboBox<String> paymentMethod, Label changeLabel) {
        Runnable updateChange = createChangeCalculator(cart, amountField, changeLabel);
        
        amountField.textProperty().addListener((obs, old, val) -> updateChange.run());
        paymentMethod.valueProperty().addListener((obs, old, val) -> updateChange.run());
        cart.addListener((ListChangeListener<CartItem>) c -> updateChange.run());
        
        setupCartItemQuantityListeners(cart, updateChange);
    }

    private Runnable createChangeCalculator(ObservableList<CartItem> cart, TextField amountField, Label changeLabel) {
        return () -> {
            PaymentCalculation calculation = calculatePaymentTotals(cart);
            String amountText = amountField.getText();
            
            if (amountText.isEmpty()) {
                changeLabel.setText("");
                return;
            }
            
            try {
                double paid = Double.parseDouble(amountText);
                double change = Math.max(0, paid - calculation.total);
                changeLabel.setText("Change: ₱" + String.format("%.2f", change));
            } catch (NumberFormatException ex) {
                changeLabel.setText("");
            }
        };
    }

    private void setupCartItemQuantityListeners(ObservableList<CartItem> cart, Runnable updateAction) {
        // Add listeners to existing items
        for (CartItem item : cart) {
            item.quantityProperty().addListener((obs, oldVal, newVal) -> updateAction.run());
        }
        
        // Add listeners to new items
        cart.addListener((ListChangeListener<CartItem>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (CartItem item : c.getAddedSubList()) {
                        item.quantityProperty().addListener((obs, oldVal, newVal) -> updateAction.run());
                    }
                }
            }
        });
    }

    private void setupPaymentHandling(ObservableList<CartItem> cart, Product[] products, 
                                    Runnable onPaymentCompleted, ComboBox<String> paymentMethod, 
                                    TextField amountField, Label errorLabel, Button payBtn) {
        payBtn.setOnAction(e -> {
            if (isReturnsMode) {
                handleRefundProcess();
            } else {
                runPaymentTask(cart, products, onPaymentCompleted, 
                             staffId, this.cashierName, paymentMethod, 
                             amountField, errorLabel, null, payBtn);
            }
        });
        
        amountField.setOnAction(e -> {
            if (!isReturnsMode) {
                runPaymentTask(cart, products, onPaymentCompleted, 
                              staffId, this.cashierName, paymentMethod, 
                              amountField, errorLabel, null, payBtn);
            }
        });
    }
    
    private void handleRefundProcess() {
        // Step 1: Validate that there are items to refund
        if (!validateRefundItems()) {
            return;
        }
        
        // Step 2: Request supervisor authorization
        if (!requestSupervisorAuthorization()) {
            return;
        }
        
        // Step 3: Show confirmation dialog
        if (!showRefundConfirmation()) {
            return;
        }
        
        // Step 4: Process the refund and exit returns mode
        processRefund();
    }
    
    private boolean validateRefundItems() {
        ReturnsManager.ReturnsSummary summary = returnsManager.calculateReturnsSummary();
        
        if (summary.refundItems <= 0) {
            showAlert("No Items to Refund", "Please select items to return by using the + button in the Action column.");
            return false;
        }
        
        if (summary.refundTotal <= 0) {
            showAlert("Invalid Refund Amount", "The refund total must be greater than ₱0.00");
            return false;
        }
        
        return true;
    }
    
    private boolean requestSupervisorAuthorization() {
        SupervisorAuthDialog.SupervisorAuthResult result = 
            SupervisorAuthDialog.requestSupervisorAuthorizationWithId();
        
        if (result.isSuccessful()) {
            lastAuthenticatedSupervisorId = result.getSupervisorId();
            return true;
        }
        
        return false;
    }
    
    private boolean showRefundConfirmation() {
        ReturnsManager.ReturnsSummary summary = returnsManager.calculateReturnsSummary();
        
        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Confirm Refund");
        confirmationDialog.setHeaderText("Process Refund - Invoice #" + returnsManager.getInvoiceNumber());
        
        String confirmationText = String.format(
            "Refund Details:\n" +
            "• Items Refund: ₱%.2f\n" +
            "• Total Refund: ₱%.2f\n\n" +
            "The refund will be processed as CASH.\n\n" +
            "Do you want to proceed with this refund?",
            summary.refundItems, summary.refundTotal
        );
        
        confirmationDialog.setContentText(confirmationText);
        confirmationDialog.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
        
        // Customize buttons
        confirmationDialog.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        Button yesButton = (Button) confirmationDialog.getDialogPane().lookupButton(ButtonType.YES);
        Button noButton = (Button) confirmationDialog.getDialogPane().lookupButton(ButtonType.NO);
        
        yesButton.setText("Process Refund");
        yesButton.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;");
        noButton.setText("Cancel");
        
        java.util.Optional<ButtonType> result = confirmationDialog.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }
    
    private void processRefund() {
        // Show loading overlay
        Platform.runLater(() -> overlay.setVisible(true));
        
        // Process refund in background thread for better performance
        javafx.concurrent.Task<Boolean> refundTask = new javafx.concurrent.Task<Boolean>() {
            private String errorMessage = "";
            private pos.db.ReturnsDAO.ReturnTransactionResult returnResult = null;
            
            @Override
            protected Boolean call() throws Exception {
                try {
                    // Get current cashier and supervisor IDs
                    int currentCashierId = staffId;
                    
                    // Get supervisor ID from authentication (you'll need to store this)
                    // For now, we'll get it from the last supervisor authentication
                    int supervisorId = getLastAuthenticatedSupervisorId();
                    if (supervisorId <= 0) {
                        errorMessage = "Supervisor authentication required";
                        return false;
                    }
                    
                    // Prepare return transaction data
                    ReturnsManager.ReturnsSummary summary = returnsManager.calculateReturnsSummary();
                    String invoiceNo = returnsManager.getInvoiceNumber();
                    
                    // Build return items data
                    List<pos.db.ReturnsDAO.ReturnItemData> returnItemsData = new ArrayList<>();
                    for (ReturnItem item : returnsManager.getReturnItems()) {
                        if (item.getQtyToReturn() > 0) {
                            returnItemsData.add(new pos.db.ReturnsDAO.ReturnItemData(
                                item.getInvoiceItemId(),
                                item.getOnlineInventoryItemId() > 0 ? item.getOnlineInventoryItemId() : null,
                                item.getInStoreInventoryId() > 0 ? item.getInStoreInventoryId() : null,
                                item.getQtyToReturn(),
                                java.math.BigDecimal.valueOf(item.getRefundAmount())
                            ));
                        }
                    }
                    
                    if (returnItemsData.isEmpty()) {
                        errorMessage = "No items selected for return";
                        return false;
                    }
                    
                    // Create return transaction data
                    pos.db.ReturnsDAO.ReturnTransactionData transactionData = 
                        new pos.db.ReturnsDAO.ReturnTransactionData(
                            invoiceNo,
                            currentCashierId,
                            supervisorId,
                            java.math.BigDecimal.valueOf(summary.refundTotal),
                            "Customer return processed via POS system",
                            returnItemsData
                        );
                    
                    // Validate data
                    String validationError = pos.db.ReturnsDAO.validateReturnData(transactionData);
                    if (validationError != null) {
                        errorMessage = validationError;
                        return false;
                    }
                    
                    // Process the return transaction atomically with return number generation
                    pos.db.ReturnsDAO.ReturnTransactionResult returnResult = pos.db.ReturnsDAO.processReturnTransactionWithReturnNo(transactionData);
                    if (returnResult != null && returnResult.returnId > 0) {
                        // Store the return result for later use in the success callback
                        this.returnResult = returnResult;
                        return true;
                    } else {
                        errorMessage = "Failed to process return transaction";
                        return false;
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    errorMessage = "Error processing refund: " + e.getMessage();
                    return false;
                }
            }
            
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    overlay.setVisible(false);
                    if (getValue()) {
                        // Success - show return receipt and confirmation
                        ReturnsManager.ReturnsSummary summary = returnsManager.calculateReturnsSummary();
                        String originalInvoiceNo = returnsManager.getInvoiceNumber();
                        
                        // Show return receipt
                        pos.view.ReturnReceiptDialog.show(
                            returnResult, 
                            new ArrayList<>(returnsManager.getReturnItems()),
                            originalInvoiceNo,
                            cashierName,
                            () -> {
                                // After showing receipt, exit returns mode
                                exitReturnsMode();
                            }
                        );
                        
                    } else {
                        // Failed - show error
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("Refund Failed");
                        errorAlert.setHeaderText("Unable to Process Refund");
                        errorAlert.setContentText(errorMessage);
                        errorAlert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
                        errorAlert.showAndWait();
                    }
                });
            }
            
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    overlay.setVisible(false);
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Refund Failed");
                    errorAlert.setHeaderText("System Error");
                    errorAlert.setContentText("An unexpected error occurred while processing the refund. Please try again.");
                    errorAlert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px;");
                    errorAlert.showAndWait();
                });
            }
        };
        
        // Run the task in background thread
        new Thread(refundTask).start();
    }
    
    private int getLastAuthenticatedSupervisorId() {
        return lastAuthenticatedSupervisorId;
    }
    
    public void setLastAuthenticatedSupervisorId(int supervisorId) {
        this.lastAuthenticatedSupervisorId = supervisorId;
    }

    private void assemblePaymentContent(Label paymentLabel, Button processReturnsBtn, ComboBox<String> paymentMethod, 
                                      TextField amountField, Label errorLabel, 
                                      Button payBtn, VBox summaryBox, VBox dateTimeBox) {
        paymentMethodLabel = new Label("Payment Method:");
        amountPaidLabel = new Label("Amount Paid");
        
        paymentContent.getChildren().addAll(
            paymentLabel, processReturnsBtn, paymentMethodLabel, paymentMethod, 
            amountPaidLabel, amountField, payBtn, errorLabel, summaryBox, dateTimeBox
        );
        paymentContent.setSpacing(COMPONENT_SPACING);
    }
    
    private void updatePaymentContentLayout() {
        if (isReturnsMode) {
            // Simple approach: Create a horizontal header box and insert it after Payment & Summary label
            HBox headerBox = new HBox();
            Label paymentLabel = new Label("Payment & Summary");
            paymentLabel.getStyleClass().add("section-title");
            
            // Ensure the button is visible and has correct text
            processReturnsButton.setText("Cancel Returns Mode");
            processReturnsButton.setVisible(true);
            processReturnsButton.setManaged(true);
            
            // Create spacer to push button to right
            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            headerBox.getChildren().addAll(paymentLabel, spacer, processReturnsButton);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            
            // Find and replace the original Payment & Summary label
            for (int i = 0; i < paymentContent.getChildren().size(); i++) {
                javafx.scene.Node node = paymentContent.getChildren().get(i);
                if (node instanceof Label && ((Label) node).getText().equals("Payment & Summary")) {
                    paymentContent.getChildren().set(i, headerBox);
                    VBox.setMargin(headerBox, new Insets(0, 0, COMPONENT_SPACING, 0));
                    break;
                }
            }
            
            // Remove the standalone button if it still exists separately
            paymentContent.getChildren().removeIf(node -> 
                node instanceof Button && node != processReturnsButton && 
                ((Button) node).getText().equals("Cancel Returns Mode")
            );
            
            System.out.println("Updated payment content layout. Cancel Returns Mode button should be visible.");
        }
    }
    
    private void restoreNormalLayout() {
        // Restore the normal layout: Payment & Summary label at top, then Process Returns button below
        System.out.println("Restoring normal payment layout.");
        
        // Find and replace the header box with just the label
        for (int i = 0; i < paymentContent.getChildren().size(); i++) {
            javafx.scene.Node node = paymentContent.getChildren().get(i);
            if (node instanceof HBox) {
                // Check if this HBox contains our Payment & Summary label
                HBox hbox = (HBox) node;
                for (javafx.scene.Node child : hbox.getChildren()) {
                    if (child instanceof Label && ((Label) child).getText().equals("Payment & Summary")) {
                        // Replace HBox with just the label
                        Label paymentLabel = new Label("Payment & Summary");
                        paymentLabel.getStyleClass().add("section-title");
                        paymentContent.getChildren().set(i, paymentLabel);
                        VBox.setMargin(paymentLabel, new Insets(0, 0, COMPONENT_SPACING, 0));
                        
                        // Make sure the process returns button is in its proper position (after the label)
                        if (!paymentContent.getChildren().contains(processReturnsButton)) {
                            paymentContent.getChildren().add(i + 1, processReturnsButton);
                        }
                        break;
                    }
                }
                break;
            }
        }
        
        // Ensure button is visible and in correct position
        processReturnsButton.setVisible(true);
        processReturnsButton.setManaged(true);
    }

    private void setupOverlayAndLoader() {
        loader.setMaxSize(LOADER_SIZE, LOADER_SIZE);
        loader.setStyle(LOADER_STYLE);
        overlay.getChildren().add(loader);
        overlay.setStyle(OVERLAY_STYLE);
        overlay.setVisible(false);
        
        StackPane root = new StackPane(paymentContent, overlay);
        getChildren().add(root);
    }

    private void setupPeriodicRefresh() {
        Timeline promoRefreshTimer = new Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.minutes(PROMOTION_REFRESH_MINUTES), 
                                        e -> refreshPromotions())
        );
        promoRefreshTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        promoRefreshTimer.play();
    }

    // Helper class for payment calculations
    private static class PaymentCalculation {
        final double subtotal;
        final double discount;
        final double tax;
        final double total;
        
        PaymentCalculation(double subtotal, double discount, double tax, double total) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.tax = tax;
            this.total = total;
        }
    }

    private PaymentCalculation calculatePaymentTotals(ObservableList<CartItem> cart) {
        applyDiscountsToCart(cart);
        double subtotal = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
        double totalDiscount = getCurrentTotalDiscount(cart);
        double tax = calculateTax(subtotal - totalDiscount);
        double total = subtotal - totalDiscount + tax;
        
        return new PaymentCalculation(subtotal, totalDiscount, tax, total);
    }

    private double calculateTax(double taxableAmount) {
        if (!cachedVatEnabled || cachedVatRate <= 0) {
            return 0.0;
        }
        return taxableAmount * cachedVatRate / 100.0;
    }

    private void setupCartListeners(VBox summaryBox, ObservableList<CartItem> cart, Label changeLabel) {
        // Extract the summary labels from summaryBox
        Label subtotalSummary = (Label) summaryBox.getChildren().get(0);
        Label totalSummary = (Label) summaryBox.getChildren().get(3);
        Label changeSummary = (Label) summaryBox.getChildren().get(4);
        
        // Bind change summary to the provided change label
        changeSummary.textProperty().bind(changeLabel.textProperty());
        
        Runnable updateSummaries = createSummaryUpdater(subtotalSummary, totalSummary, cart);
        cart.addListener((ListChangeListener<CartItem>) c -> updateSummaries.run());
        setupCartItemQuantityListeners(cart, updateSummaries);
        updateSummaries.run();
    }

    private Runnable createSummaryUpdater(Label subtotalSummary, Label totalSummary, ObservableList<CartItem> cart) {
        return () -> {
            PaymentCalculation calculation = calculatePaymentTotals(cart);
            
            subtotalSummary.setText("Subtotal: ₱" + String.format("%.2f", calculation.subtotal));
            discountSummary.setText("Discount: -₱" + String.format("%.2f", calculation.discount));
            
            int vatRate = cachedVatEnabled ? cachedVatRate : 0;
            taxSummary.setText("Tax (" + vatRate + "%): +₱" + String.format("%.2f", calculation.tax));
            totalSummary.setText("Total: ₱" + String.format("%.2f", calculation.total));
        };
    }

    private double getCurrentTotalDiscount(ObservableList<CartItem> cart) {
        double totalDiscount = 0;
        for (CartItem item : cart) {
            totalDiscount += item.getDiscount();
        }
        return totalDiscount;
    }

    private void runPaymentTask(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted, 
                               int staffId, String cashierName, ComboBox<String> paymentMethod, 
                               TextField amountField, Label errorLabel, Label changeLabel, Button payBtn) {
        payBtn.setDisable(true);
        
        Task<Void> paymentTask = createPaymentTask(cart, products, onPaymentCompleted, staffId, cashierName, 
                                                 paymentMethod, amountField, errorLabel, changeLabel, payBtn);
        
        new Thread(paymentTask).start();
    }

    private Task<Void> createPaymentTask(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted, 
                                       int staffId, String cashierName, ComboBox<String> paymentMethod, 
                                       TextField amountField, Label errorLabel, Label changeLabel, Button payBtn) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    PaymentValidationResult validation = validatePaymentInput(cart, paymentMethod, amountField);
                    
                    if (!validation.isValid) {
                        showValidationError(validation.errorMessage, errorLabel, payBtn);
                        return null;
                    }
                    
                    boolean confirmed = showPaymentConfirmation();
                    if (!confirmed) {
                        hideOverlayAndEnableButton(payBtn);
                        return null;
                    }
                    
                    Platform.runLater(() -> overlay.setVisible(true));
                    
                    processPayment(cart, validation.calculation.subtotal, validation.calculation.discount, 
                                 validation.calculation.tax, validation.calculation.total, validation.paidAmount, 
                                 validation.isEwallet, validation.referenceNumber, onPaymentCompleted, 
                                 amountField, changeLabel, paymentMethod, cashierName, payBtn, errorLabel);
                    
                } catch (Exception ex) {
                    handlePaymentError(ex, errorLabel);
                }
                
                Platform.runLater(() -> {
                    overlay.setVisible(false);
                    payBtn.setDisable(false);
                });
                
                return null;
            }
        };
    }

    private PaymentValidationResult validatePaymentInput(ObservableList<CartItem> cart, ComboBox<String> paymentMethod, 
                                                        TextField amountField) {
        PaymentCalculation calculation = calculatePaymentTotals(cart);
        String amountText = amountField.getText();
        boolean isEwallet = PAYMENT_METHOD_EWALLET.equals(paymentMethod.getValue());
        String referenceNumber = isEwallet ? refNoField.getText().trim() : null;
        
        if (cart.isEmpty()) {
            return PaymentValidationResult.invalid("Cart is empty. Please add items to the cart.");
        }
        
        if (amountText.isEmpty()) {
            return PaymentValidationResult.invalid("Please enter amount paid.");
        }
        
        if (isEwallet && (referenceNumber == null || referenceNumber.isEmpty())) {
            return PaymentValidationResult.invalid("Reference number is required for E-Wallet payments.");
        }
        
        try {
            double paidAmount = Double.parseDouble(amountText);
            
            if (paidAmount < calculation.total) {
                String paymentType = PAYMENT_METHOD_CASH.equals(paymentMethod.getValue()) ? "cash" : "amount";
                return PaymentValidationResult.invalid("Insufficient " + paymentType + ".");
            }
            
            return PaymentValidationResult.valid(calculation, paidAmount, isEwallet, referenceNumber);
            
        } catch (NumberFormatException ex) {
            return PaymentValidationResult.invalid("Invalid amount.");
        }
    }

    private void showValidationError(String errorMessage, Label errorLabel, Button payBtn) {
        Platform.runLater(() -> {
            errorLabel.setText(errorMessage);
            overlay.setVisible(false);
            payBtn.setDisable(false);
        });
    }

    private boolean showPaymentConfirmation() throws InterruptedException {
        final boolean[] confirmed = {false};
        CountDownLatch latch = new CountDownLatch(1);
        
        Platform.runLater(() -> {
            Alert confirm = createPaymentConfirmationDialog();
            confirm.showAndWait().ifPresent(type -> {
                confirmed[0] = (type == ButtonType.OK);
                latch.countDown();
            });
        });
        
        latch.await();
        return confirmed[0];
    }

    private Alert createPaymentConfirmationDialog() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Payment");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to complete this payment?");
        confirm.initModality(Modality.APPLICATION_MODAL);
        return confirm;
    }

    private void hideOverlayAndEnableButton(Button payBtn) {
        Platform.runLater(() -> {
            overlay.setVisible(false);
            payBtn.setDisable(false);
        });
    }

    private void handlePaymentError(Exception ex, Label errorLabel) {
        ex.printStackTrace();
        Platform.runLater(() -> errorLabel.setText("Error processing payment: " + ex.getMessage()));
    }

    // Helper class for payment validation results
    private static class PaymentValidationResult {
        final boolean isValid;
        final String errorMessage;
        final PaymentCalculation calculation;
        final double paidAmount;
        final boolean isEwallet;
        final String referenceNumber;
        
        private PaymentValidationResult(boolean isValid, String errorMessage, PaymentCalculation calculation, 
                                      double paidAmount, boolean isEwallet, String referenceNumber) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.calculation = calculation;
            this.paidAmount = paidAmount;
            this.isEwallet = isEwallet;
            this.referenceNumber = referenceNumber;
        }
        
        static PaymentValidationResult invalid(String errorMessage) {
            return new PaymentValidationResult(false, errorMessage, null, 0, false, null);
        }
        
        static PaymentValidationResult valid(PaymentCalculation calculation, double paidAmount, 
                                           boolean isEwallet, String referenceNumber) {
            return new PaymentValidationResult(true, null, calculation, paidAmount, isEwallet, referenceNumber);
        }
    }

    private void processPayment(ObservableList<CartItem> cart, double subtotal, double discount, double tax, 
                               double total, double paid, boolean isEwallet, String refNo, Runnable onPaymentCompleted, 
                               TextField amountField, Label changeLabel, ComboBox<String> paymentMethod, 
                               String cashierName, Button payBtn, Label errorLabel) {
        java.sql.Connection conn = null;
        try {
            conn = pos.db.DBConnection.getConnection();
            conn.setAutoCommit(false);
            
            TransactionData transactionData = createTransaction(conn, subtotal, discount, tax, total, paid, 
                                                              paymentMethod.getValue(), isEwallet, refNo);
            insertSaleItems(conn, cart, transactionData.posTransactionId);
            updateInventory(conn, cart);
            
            conn.commit();
            
            showSuccessAndReset(cart, paid, total, paymentMethod.getValue(), onPaymentCompleted, 
                              amountField, changeLabel, paymentMethod, cashierName, 
                              transactionData.receiptNumber, discount, tax);
            
        } catch (Exception ex) {
            rollbackTransaction(conn, ex, errorLabel);
        } finally {
            closeConnection(conn);
        }
    }

    private TransactionData createTransaction(java.sql.Connection conn, double subtotal, double discount, 
                                            double tax, double total, double paid, String paymentMethodValue, 
                                            boolean isEwallet, String refNo) throws Exception {
        String receiptNumber = PosTransactionDAO.generateNextInvoiceNo(conn);
        
        int posTransactionId = PosTransactionDAO.insertPosTransaction(
            conn, receiptNumber, new Timestamp(System.currentTimeMillis()),
            paymentMethodValue, staffId, subtotal, discount, tax, total, paid,
            isEwallet ? refNo : null
        );
        
        String transactionId = PosTransactionDAO.generateNextTransactionId(conn);
        PosTransactionDAO.insertTransactionLog(
            conn, transactionId, null, posTransactionId, null,
            "in-store", "sale", "completed"
        );
        
        return new TransactionData(receiptNumber, posTransactionId);
    }

    private void insertSaleItems(java.sql.Connection conn, ObservableList<CartItem> cart, int posTransactionId) throws Exception {
        List<Map<String, Object>> items = prepareSaleItems(conn, cart);
        PosTransactionDAO.insertPhysicalSaleItems(conn, posTransactionId, items);
    }

    private List<Map<String, Object>> prepareSaleItems(java.sql.Connection conn, ObservableList<CartItem> cart) {
        List<Map<String, Object>> items = new ArrayList<>();
        
        for (CartItem item : cart) {
            Map<String, Object> saleItem = createSaleItemData(conn, item);
            items.add(saleItem);
        }
        
        return items;
    }

    private Map<String, Object> createSaleItemData(java.sql.Connection conn, CartItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put("sku", item.getProduct().getSku());
        row.put("order_quantity", item.getQuantity());
        row.put("stock_quantity", item.getProduct().getQuantity());
        row.put("subtotal", item.getSubtotal());
        
        try {
            InventoryItemData inventoryData = getInventoryItemData(conn, item.getProduct().getSku());
            row.put("sale_channel", inventoryData.saleChannel);
            row.put("online_inventory_item_id", inventoryData.onlineInventoryItemId);
            row.put("in_store_inventory_item_id", inventoryData.inStoreInventoryItemId);
        } catch (RuntimeException e) {
            // Re-throw with more context about which item failed
            throw new RuntimeException("Failed to process item '" + item.getProduct().getDescription() + 
                "' (SKU: " + item.getProduct().getSku() + "): " + e.getMessage(), e);
        }
        
        return row;
    }

    private InventoryItemData getInventoryItemData(java.sql.Connection conn, String sku) {
        try {
            ProductDAO.InventoryItemInfo info = ProductDAO.getInventoryItemInfoBySku(conn, sku);
            if (info != null) {
                String saleChannel = info.saleChannel;
                Integer onlineId = null;
                Integer inStoreId = null;
                
                // For 'both' and 'online' products, use online inventory item ID
                if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
                    onlineId = info.inventoryItemId;
                }
                // For 'in-store' products, use in-store inventory item ID  
                else if ("in-store".equalsIgnoreCase(saleChannel)) {
                    inStoreId = info.inventoryItemId;
                }
                
                return new InventoryItemData(saleChannel, onlineId, inStoreId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return new InventoryItemData("in-store", null, null);
    }

    private void updateInventory(java.sql.Connection conn, ObservableList<CartItem> cart) {
        Map<String, Integer> inStoreMap = new HashMap<>();
        Map<String, Integer> onlineMap = new HashMap<>();
        
        categorizeInventoryUpdates(conn, cart, inStoreMap, onlineMap);
        executeInventoryUpdates(conn, inStoreMap, onlineMap);
    }

    private void categorizeInventoryUpdates(java.sql.Connection conn, ObservableList<CartItem> cart,
                                          Map<String, Integer> inStoreMap, Map<String, Integer> onlineMap) {
        for (CartItem item : cart) {
            String sku = item.getProduct().getSku();
            int quantity = item.getQuantity();
            String saleChannel = getSaleChannel(conn, sku);
            
            if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
                onlineMap.put(sku, quantity);
            } else {
                inStoreMap.put(sku, quantity);
            }
        }
    }

    private String getSaleChannel(java.sql.Connection conn, String sku) {
        try {
            ProductDAO.InventoryInfo info = ProductDAO.getInventoryInfoBySku(conn, sku);
            return info != null ? info.saleChannel : "in-store";
        } catch (Exception e) {
            e.printStackTrace();
            return "in-store";
        }
    }

    private void executeInventoryUpdates(java.sql.Connection conn, Map<String, Integer> inStoreMap, Map<String, Integer> onlineMap) {
        try {
            if (!inStoreMap.isEmpty()) {
                ProductDAO.batchUpdateInventory(conn, inStoreMap, "in-store");
            }
            if (!onlineMap.isEmpty()) {
                ProductDAO.batchUpdateInventory(conn, onlineMap, "both");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSuccessAndReset(ObservableList<CartItem> cart, double paid, double total, String paymentMethodValue,
                                   Runnable onPaymentCompleted, TextField amountField, Label changeLabel,
                                   ComboBox<String> paymentMethod, String cashierName, String receiptNumber,
                                   double discount, double tax) {
        javafx.application.Platform.runLater(() -> {
            ReceiptDialog.show(cart, paid, total, paymentMethodValue, paid - total, () -> {
                onPaymentCompleted.run();
                resetPaymentForm(cart, amountField, paymentMethod);
            }, cashierName, receiptNumber, discount, tax);
        });
    }

    private void resetPaymentForm(ObservableList<CartItem> cart, TextField amountField, ComboBox<String> paymentMethod) {
        cart.clear();
        amountField.clear();
        changeLabel.setText("");
        paymentMethod.setValue(PAYMENT_METHOD_CASH);
        refNoField.clear();
        refNoBox.setVisible(false);
        refNoBox.setManaged(false);
    }

    private void rollbackTransaction(java.sql.Connection conn, Exception ex, Label errorLabel) {
        if (conn != null) {
            try { 
                conn.rollback(); 
            } catch (Exception ignore) {}
        }
        ex.printStackTrace();
        javafx.application.Platform.runLater(() -> errorLabel.setText("Error processing transaction."));
    }

    private void closeConnection(java.sql.Connection conn) {
        if (conn != null) {
            try { 
                conn.close(); 
            } catch (Exception ignore) {}
        }
    }

    // Helper classes for data transfer
    private static class TransactionData {
        final String receiptNumber;
        final int posTransactionId;
        
        TransactionData(String receiptNumber, int posTransactionId) {
            this.receiptNumber = receiptNumber;
            this.posTransactionId = posTransactionId;
        }
    }

    private static class InventoryItemData {
        final String saleChannel;
        final Integer onlineInventoryItemId;
        final Integer inStoreInventoryItemId;
        
        InventoryItemData(String saleChannel, Integer onlineInventoryItemId, Integer inStoreInventoryItemId) {
            this.saleChannel = saleChannel;
            this.onlineInventoryItemId = onlineInventoryItemId;
            this.inStoreInventoryItemId = inStoreInventoryItemId;
        }
    }

    private void refreshPromotions() {
        loadPromotions();
    }

    private void refreshVatSettings() {
        try {
            cachedVatRate = SystemSettingsDAO.getVatRate();
            cachedVatEnabled = SystemSettingsDAO.isVatEnabled();
        } catch (Exception e) {
            cachedVatRate = 0;
            cachedVatEnabled = false;
        }
    }

    private void applyDiscountsToCart(ObservableList<CartItem> cart) {
        for (CartItem item : cart) {
            double discount = calculateItemDiscount(item);
            item.setDiscount(discount);
        }
    }

    private double calculateItemDiscount(CartItem item) {
        double price = item.getProduct().getPrice();
        int quantity = item.getQuantity();
        int categoryId = item.getProduct().getCategoryId();
        
        pos.db.PromotionDao.Promotion bestPromo = pos.db.PromotionDao.getBestPromotionForItem(
            null, item.getProduct().getSku(), categoryId, price, quantity, cachedPromotions
        );
        
        if (bestPromo == null) {
            return 0.0;
        }
        
        if ("percentage".equals(bestPromo.type)) {
            return price * quantity * (bestPromo.value / 100.0);
        } else if ("fixed".equals(bestPromo.type)) {
            return Math.min(bestPromo.value, price * quantity);
        }
        
        return 0.0;
    }
} 