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

public class PaymentSectionView extends VBox {
    public PaymentSectionView(ObservableList<CartItem> cart, Product[] products, Runnable onPaymentCompleted) {
        setPadding(new Insets(10));
        setPrefWidth(300);
        getStyleClass().add("card");
        Label paymentLabel = new Label("Payment & Summary");
        paymentLabel.getStyleClass().add("section-title");
        VBox.setMargin(paymentLabel, new Insets(0, 0, 10, 0));
        Label paymentMethodLabel = new Label("Payment Method:");
        ComboBox<String> paymentMethod = new ComboBox<>();
        paymentMethod.getItems().addAll("Cash", "Credit Card");
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
        Label discountSummary = new Label("Discount: 0%");
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
                if (paymentMethod.getValue().equals("Cash")) {
                    if (paid < total) {
                        changeLabel.setText("Change: ₱0.00");
                    } else {
                        double change = paid - total;
                        changeLabel.setText("Change: ₱" + String.format("%.2f", change));
                    }
                } else {
                    if (paid < total) {
                        changeLabel.setText("Change: ₱0.00");
                    } else {
                        changeLabel.setText("Payment successful (Credit Card). Change: ₱0.00");
                    }
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
        payBtn.setOnAction(e -> {
            double total = cart.stream().mapToDouble(CartItem::getSubtotal).sum();
            String amtStr = amountField.getText();
            errorLabel.setText("");
            if (amtStr.isEmpty()) {
                errorLabel.setText("Please enter amount paid.");
                return;
            }
            try {
                double paid = Double.parseDouble(amtStr);
                if (paymentMethod.getValue().equals("Cash")) {
                    if (paid < total) {
                        errorLabel.setText("Insufficient cash.");
                    } else {
                        // Show confirmation dialog
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Confirm Payment");
                        confirm.setHeaderText(null);
                        confirm.setContentText("Are you sure you want to complete this payment?");
                        confirm.initModality(Modality.APPLICATION_MODAL);
                        confirm.showAndWait().ifPresent(type -> {
                            if (type == ButtonType.OK) {
                                ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {});
                                onPaymentCompleted.run();
                                cart.clear();
                                amountField.clear();
                                changeLabel.setText("");
                            }
                        });
                    }
                } else {
                    if (paid < total) {
                        errorLabel.setText("Insufficient amount.");
                    } else {
                        // Show confirmation dialog
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Confirm Payment");
                        confirm.setHeaderText(null);
                        confirm.setContentText("Are you sure you want to complete this payment?");
                        confirm.initModality(Modality.APPLICATION_MODAL);
                        confirm.showAndWait().ifPresent(type -> {
                            if (type == ButtonType.OK) {
                                ReceiptDialog.show(cart, paid, total, paymentMethod.getValue(), paid - total, () -> {});
                                onPaymentCompleted.run();
                                cart.clear();
                                amountField.clear();
                                changeLabel.setText("");
                            }
                        });
                    }
                }
            } catch (NumberFormatException ex) {
                errorLabel.setText("Invalid amount.");
            }
        });
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