package pos.view;

import javafx.collections.ObservableList;
import pos.model.CartItem;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.text.TextAlignment;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.printing.PDFPageable;
import java.awt.print.PrinterJob;
import java.awt.print.PrinterAbortException;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.JasperPrintManager;
import javafx.application.Platform;

public class ReceiptDialog {
    public static void show(ObservableList<CartItem> cartSnapshot, double paid, double total, String paymentType, double change, Runnable afterPrint, String cashierName, String receiptNumber, double discount, double tax) {
        int vatRate = 0;
        if (total - discount > 0.01) {
            vatRate = (int)Math.round((tax / (total - discount)) * 100);
        }
        try {
            generateAndPrintJasperReceipt(cartSnapshot, paid, total, paymentType, change, "", "", cashierName, receiptNumber, discount, tax, vatRate);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (afterPrint != null) afterPrint.run();
    }

    private static void generateAndPrintJasperReceipt(
        ObservableList<CartItem> cartSnapshot, double paid, double totalInput, String paymentType, double change, String customerName, String customerPhone, String cashierName, String receiptNumber, double discount, double tax, int vatRate
    ) throws Exception {
        // Calculate values
        double subtotal = cartSnapshot.stream().mapToDouble(CartItem::getSubtotal).sum();
        double total = subtotal - discount + tax;

        // Prepare parameters
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("StoreName", "Dream Fashion Shop");
        params.put("StoreAddress", "123 Main St, City, Country");
        params.put("StorePhone", "123-456-7890");
        // Extract integer from zero-padded receipt number
        String receiptNumOnly = receiptNumber.replaceFirst("^0+", "");
        if (receiptNumOnly.isEmpty()) receiptNumOnly = "0";
        params.put("ReceiptNumber", receiptNumOnly); // only the number part
        params.put("DateTime", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("Cashier", cashierName);
        params.put("Subtotal", subtotal);
        params.put("Discount", discount);
        params.put("TaxLabel", "Tax (" + vatRate + "%)");
        params.put("Tax", tax);
        params.put("Total", total);
        params.put("Paid", paid);
        params.put("Change", change);
        params.put("CustomerName", customerName);
        params.put("CustomerPhone", customerPhone);
        params.put("PaymentMethod", paymentType);
        params.put("DiscountSign", "-");
        params.put("TaxSign", "+");

        // Prepare item data
        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        for (CartItem item : cartSnapshot) {
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("ItemName", item.getProduct().getSku());
            row.put("Quantity", item.getQuantity());
            row.put("UnitPrice", item.getProduct().getPrice());
            row.put("TotalPrice", item.getSubtotal());
            items.add(row);
        }
        net.sf.jasperreports.engine.data.JRBeanCollectionDataSource dataSource = new net.sf.jasperreports.engine.data.JRBeanCollectionDataSource(items);

        // Compile and fill the report
        net.sf.jasperreports.engine.JasperReport jasperReport = net.sf.jasperreports.engine.JasperCompileManager.compileReport("receipt_template.jrxml");
        net.sf.jasperreports.engine.JasperPrint jasperPrint = net.sf.jasperreports.engine.JasperFillManager.fillReport(jasperReport, params, dataSource);

        // Now print
        //JasperPrintManager.printReport(jasperPrint, true);

        // Debug prints and show JasperViewer on JavaFX thread
        System.out.println("About to show receipt...");
        Platform.runLater(() -> {
            net.sf.jasperreports.view.JasperViewer.viewReport(jasperPrint, false);
            System.out.println("Receipt dialog should have appeared.");
        });

        
    }
} 