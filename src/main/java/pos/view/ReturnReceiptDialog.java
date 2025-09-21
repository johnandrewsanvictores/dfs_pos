package pos.view;

import javafx.application.Platform;
import pos.model.ReturnItem;
import pos.db.ReturnsDAO;
import pos.db.DBConnection;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog for displaying and printing return receipts
 * Generates receipts for refund transactions using JasperReports
 */
public class ReturnReceiptDialog {
    
    /**
     * Show return receipt dialog with refund information
     * @param returnResult Return transaction result with return number and details
     * @param returnItems List of returned items
     * @param originalInvoiceNo Original invoice number
     * @param cashierName Name of the cashier processing the return
     * @param afterPrint Callback to run after receipt is displayed
     */
    public static void show(ReturnsDAO.ReturnTransactionResult returnResult, 
                          List<ReturnItem> returnItems, 
                          String originalInvoiceNo, 
                          String cashierName,
                          Runnable afterPrint) {
        try {
            generateAndPrintReturnReceipt(
                returnResult.returnNo,
                returnResult.returnId,
                originalInvoiceNo,
                returnItems,
                returnResult.refundTotal.doubleValue(),
                cashierName
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (afterPrint != null) afterPrint.run();
    }
    
    /**
     * Generate and display return receipt using JasperReports
     */
    private static void generateAndPrintReturnReceipt(
        String returnNo,
        int returnId,
        String originalInvoiceNo,
        List<ReturnItem> returnItems,
        double refundTotal,
        String cashierName
    ) throws Exception {
        
        // For now, use the existing template with return modifications
        generateReturnReceiptWithExistingTemplate(
            returnNo, returnId, originalInvoiceNo, returnItems, refundTotal, cashierName
        );
    }
    
    /**
     * Generate and show return receipt with proportional tax calculation
     * and detailed format matching sales invoice structure
     */
    private static void generateReturnReceiptWithExistingTemplate(
        String returnNo,
        int returnId,
        String originalInvoiceNo,
        List<ReturnItem> returnItems,
        double refundTotal,
        String cashierName
    ) throws Exception {
        
        // Get original transaction data to calculate proportional tax
        Connection conn = DBConnection.getConnection();
        ReturnsDAO.InvoiceData originalData = ReturnsDAO.getInvoiceData(conn, originalInvoiceNo);
        
        // Get return transaction details for refund method and reason
        ReturnsDAO.ReturnDetails returnDetails = ReturnsDAO.getReturnDetails(conn, returnId);
        
        conn.close();
        
        if (originalData == null) {
            throw new Exception("Original invoice data not found");
        }
        
        // Calculate proportional tax for returned items
        double refundSubtotal = returnItems.stream().mapToDouble(ReturnItem::getRefundAmount).sum();
        double proportionalTax = calculateProportionalTax(
            refundSubtotal, 
            originalData.subtotal, 
            originalData.discount, 
            originalData.tax
        );
        
        double totalRefundWithTax = refundSubtotal + proportionalTax;
        
        // Prepare parameters for new return receipt template
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", "Dreams Fashion Shop");
        params.put("companyAddress", "Dinglasan Building Quezon Avenue Brgy. 1 (Pob.) 4301, Lucena City");
        params.put("companyPhone", "Non-VAT Reg.TIN No. 484-537-143-00000");
        params.put("returnNo", returnNo);
        params.put("originalInvoiceNo", originalInvoiceNo);
        params.put("originalTransactionDate", formatDateFromTimestamp(originalData.transactionDate));
        params.put("returnDate", formatCurrentDate());
        params.put("cashierName", cashierName);
        params.put("customerName", originalData.customerName != null ? originalData.customerName : "");
        
        // Refund method and reason from return transaction
        params.put("refundMethod", returnDetails != null ? returnDetails.refundMethod : "Cash");
        params.put("refundReason", returnDetails != null ? returnDetails.notes : "Customer return");
        
        // Original transaction totals
        params.put("originalSubtotal", new java.math.BigDecimal(originalData.subtotal));
        params.put("originalDiscount", new java.math.BigDecimal(originalData.discount));
        params.put("originalTax", new java.math.BigDecimal(originalData.tax));
        params.put("originalTotal", new java.math.BigDecimal(
            originalData.subtotal - originalData.discount + originalData.tax));
        
        // Refund totals with proportional tax
        params.put("refundSubtotal", new java.math.BigDecimal(refundSubtotal));
        params.put("refundTax", new java.math.BigDecimal(proportionalTax));
        params.put("refundTotal", new java.math.BigDecimal(totalRefundWithTax));
        
        // Prepare item data for the receipt with detailed breakdown
        List<Map<String, Object>> items = new ArrayList<>();
        for (ReturnItem item : returnItems) {
            Map<String, Object> row = new HashMap<>();
            row.put("productName", item.getProductName());
            row.put("returnQuantity", item.getQtyToReturn());
            row.put("originalPrice", new java.math.BigDecimal(item.getPrice()));
            row.put("discountAmount", new java.math.BigDecimal(item.getDiscount()));
            row.put("refundAmount", new java.math.BigDecimal(item.getRefundAmount()));
            items.add(row);
        }
        
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(items);
        
        // Use the new return receipt template from resources
        InputStream templateStream = ReturnReceiptDialog.class.getResourceAsStream("/return_receipt_template.jrxml");
        if (templateStream == null) {
            throw new Exception("Return receipt template not found in resources");
        }
        JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
        
        // Show the return receipt on JavaFX thread
        System.out.println("About to show return receipt with proportional tax...");
        Platform.runLater(() -> {
            net.sf.jasperreports.view.JasperViewer.viewReport(jasperPrint, false);
            System.out.println("Return receipt dialog should have appeared.");
        });
    }
    
    /**
     * Calculate proportional tax for returned items
     */
    private static double calculateProportionalTax(double refundSubtotal, double originalSubtotal, 
                                                  double originalDiscount, double originalTax) {
        if (originalTax <= 0 || originalSubtotal <= 0) {
            return 0.0; // No tax to calculate
        }
        
        // Calculate the tax rate from original transaction
        double originalTaxableAmount = originalSubtotal - originalDiscount;
        if (originalTaxableAmount <= 0) {
            return 0.0;
        }
        
        double taxRate = originalTax / originalTaxableAmount;
        
        // Apply the same tax rate to the returned items (already includes discounts)
        return refundSubtotal * taxRate;
    }
    
    /**
     * Format timestamp to readable date string
     */
    private static String formatDateFromTimestamp(java.sql.Timestamp timestamp) {
        return timestamp.toLocalDateTime().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * Format current date
     */
    private static String formatCurrentDate() {
        return java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}