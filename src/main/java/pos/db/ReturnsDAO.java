package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for handling return transactions
 * Provides atomic operations for processing returns with proper error handling
 */
public class ReturnsDAO {
    
    /**
     * Result class for invoice validation
     */
    public static class InvoiceValidationResult {
        public final boolean isValid;
        public final String errorMessage;
        
        public InvoiceValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
        
        public static InvoiceValidationResult valid() {
            return new InvoiceValidationResult(true, null);
        }
        
        public static InvoiceValidationResult invalid(String errorMessage) {
            return new InvoiceValidationResult(false, errorMessage);
        }
    }
    
    /**
     * Validates if an invoice number can be processed for returns
     * @param conn Database connection
     * @param invoiceNo Invoice number to validate
     * @return Validation result with error message if invalid
     * @throws SQLException if database error occurs
     */
    /**
     * Data class to hold original invoice/transaction data for return receipt generation
     */
    public static class InvoiceData {
        public final double subtotal;
        public final double discount;
        public final double tax;
        public final String customerName;
        public final java.sql.Timestamp transactionDate;
        
        public InvoiceData(double subtotal, double discount, double tax, String customerName, java.sql.Timestamp transactionDate) {
            this.subtotal = subtotal;
            this.discount = discount;
            this.tax = tax;
            this.customerName = customerName;
            this.transactionDate = transactionDate;
        }
    }
    
    /**
     * Get original transaction data for return receipt generation
     */
    public static InvoiceData getInvoiceData(Connection conn, String invoiceNo) throws SQLException {
        String query = "SELECT subtotal, discount, tax, customer_name, transaction_date FROM pos_transactions WHERE invoice_no = ?";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, invoiceNo);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            InvoiceData data = new InvoiceData(
                rs.getDouble("subtotal"),
                rs.getDouble("discount"),
                rs.getDouble("tax"),
                rs.getString("customer_name"),
                rs.getTimestamp("transaction_date")
            );
            rs.close();
            stmt.close();
            return data;
        }
        
        rs.close();
        stmt.close();
        return null;
    }
    
    public static InvoiceValidationResult validateInvoiceForReturns(Connection conn, String invoiceNo) throws SQLException {
        // 1. Check if invoice exists and get transaction date
        String checkInvoiceQuery = "SELECT id, transaction_date FROM pos_transactions WHERE invoice_no = ?";
        PreparedStatement stmt = conn.prepareStatement(checkInvoiceQuery);
        stmt.setString(1, invoiceNo);
        ResultSet rs = stmt.executeQuery();
        
        if (!rs.next()) {
            rs.close();
            stmt.close();
            return InvoiceValidationResult.invalid("Invoice number '" + invoiceNo + "' not found. Please check the invoice number and try again.");
        }
        
        int posTransactionId = rs.getInt("id");
        java.sql.Timestamp transactionDate = rs.getTimestamp("transaction_date");
        rs.close();
        stmt.close();
        
        // 2. Check if transaction is not more than 7 days old
        long currentTime = System.currentTimeMillis();
        long transactionTime = transactionDate.getTime();
        long daysDifference = (currentTime - transactionTime) / (1000 * 60 * 60 * 24);
        
        if (daysDifference > 7) {
            return InvoiceValidationResult.invalid("Invoice '" + invoiceNo + "' is more than 7 days old and cannot be returned. Returns are only allowed within 7 days of purchase.");
        }
        
        // 3. Check if invoice has already been processed for returns
        String checkReturnsQuery = "SELECT COUNT(*) as return_count FROM pos_returns WHERE invoice_no = ?";
        PreparedStatement returnsStmt = conn.prepareStatement(checkReturnsQuery);
        returnsStmt.setString(1, invoiceNo);
        ResultSet returnsRs = returnsStmt.executeQuery();
        
        if (returnsRs.next() && returnsRs.getInt("return_count") > 0) {
            returnsRs.close();
            returnsStmt.close();
            return InvoiceValidationResult.invalid("Invoice '" + invoiceNo + "' has already been processed for returns. Each invoice can only be returned once.");
        }
        
        returnsRs.close();
        returnsStmt.close();
        
        return InvoiceValidationResult.valid();
    }
    
    /**
     * Data class to represent a return item for processing
     */
    public static class ReturnItemData {
        public final int invoiceItemId;
        public final int onlineInventoryItemId; // nullable
        public final int inStoreInventoryId; // nullable  
        public final int qtyReturned;
        public final BigDecimal refundAmount;
        
        public ReturnItemData(int invoiceItemId, Integer onlineInventoryItemId, Integer inStoreInventoryId, 
                            int qtyReturned, BigDecimal refundAmount) {
            this.invoiceItemId = invoiceItemId;
            this.onlineInventoryItemId = onlineInventoryItemId != null ? onlineInventoryItemId : 0;
            this.inStoreInventoryId = inStoreInventoryId != null ? inStoreInventoryId : 0;
            this.qtyReturned = qtyReturned;
            this.refundAmount = refundAmount;
        }
    }
    
    /**
     * Data class to represent the complete return transaction
     */
    public static class ReturnTransactionData {
        public final String invoiceNo;
        public final int cashierId;
        public final int supervisorId;
        public final BigDecimal refundTotal;
        public final String notes;
        public final List<ReturnItemData> returnItems;
        
        public ReturnTransactionData(String invoiceNo, int cashierId, int supervisorId, 
                                   BigDecimal refundTotal, String notes, List<ReturnItemData> returnItems) {
            this.invoiceNo = invoiceNo;
            this.cashierId = cashierId;
            this.supervisorId = supervisorId;
            this.refundTotal = refundTotal;
            this.notes = notes;
            this.returnItems = returnItems;
        }
    }
    
    /**
     * Generate the next return number in the format RTN-XXXXXX
     * @param conn Database connection
     * @return Next return number
     * @throws SQLException if database error occurs
     */
    public static String generateNextReturnNo(Connection conn) throws SQLException {
        String sql = "SELECT return_no FROM pos_returns ORDER BY return_id DESC LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();
        int next = 1;
        if (rs.next()) {
            String last = rs.getString("return_no");
            try {
                // Extract number from RTN-XXXXXX format
                String numberPart = last.substring(4);
                next = Integer.parseInt(numberPart) + 1;
            } catch (Exception ignored) {}
        }
        rs.close();
        stmt.close();
        return String.format("RTN-%06d", next); // 6 digits, leading zeros
    }

    /**
     * Data class to represent the complete return transaction with return number
     */
    public static class ReturnTransactionResult {
        public final int returnId;
        public final String returnNo;
        public final BigDecimal refundTotal;
        public final List<ReturnItemData> returnItems;
        
        public ReturnTransactionResult(int returnId, String returnNo, BigDecimal refundTotal, List<ReturnItemData> returnItems) {
            this.returnId = returnId;
            this.returnNo = returnNo;
            this.refundTotal = refundTotal;
            this.returnItems = returnItems;
        }
    }

    /**
     * Process complete return transaction atomically with return number generation
     * @param returnData Complete return transaction data
     * @return ReturnTransactionResult with return ID and number if successful, null if failed
     * @throws SQLException if database operation fails
     */
    public static ReturnTransactionResult processReturnTransactionWithReturnNo(ReturnTransactionData returnData) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Generate return number
            String returnNo = generateNextReturnNo(conn);
            
            // 2. Insert main return record with return number
            int returnId = insertReturnWithReturnNo(conn, returnData, returnNo);
            if (returnId == -1) {
                throw new SQLException("Failed to insert return record");
            }
            
            // 3. Insert all return items
            insertReturnItems(conn, returnId, returnData.returnItems);
            
            // 4. Update inventory quantities (increase stock)
            updateInventoryQuantities(conn, returnData.returnItems);
            
            // 5. Log supervisor authorization activity
            logReturnAuthorization(conn, returnData.supervisorId, returnData.invoiceNo, 
                                 returnData.refundTotal, returnData.cashierId);
            
            // 6. Log cashier return processing activity
            logReturnProcessing(conn, returnData.cashierId, returnData.invoiceNo, 
                              returnData.refundTotal, returnData.supervisorId);
            
            conn.commit(); // All operations successful
            return new ReturnTransactionResult(returnId, returnNo, returnData.refundTotal, returnData.returnItems);
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback on any failure
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw new SQLException("Return transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restore auto-commit
                    conn.close();
                } catch (SQLException closeEx) {
                    // Log but don't throw
                    System.err.println("Warning: Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }
    
    /**
     * Insert main return record with return number
     */
    private static int insertReturnWithReturnNo(Connection conn, ReturnTransactionData returnData, String returnNo) throws SQLException {
        String sql = "INSERT INTO pos_returns (return_no, invoice_no, cashier_id, supervisor_id, refund_total, refund_method, notes) " +
                    "VALUES (?, ?, ?, ?, ?, 'Cash', ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, returnNo);
            stmt.setString(2, returnData.invoiceNo);
            stmt.setInt(3, returnData.cashierId);
            stmt.setInt(4, returnData.supervisorId);
            stmt.setBigDecimal(5, returnData.refundTotal);
            stmt.setString(6, returnData.notes);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return -1;
            }
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Insert main return record (deprecated - use insertReturnWithReturnNo instead)
     */
    private static int insertReturn(Connection conn, ReturnTransactionData returnData) throws SQLException {
        String sql = "INSERT INTO pos_returns (invoice_no, cashier_id, supervisor_id, refund_total, refund_method, notes) " +
                    "VALUES (?, ?, ?, ?, 'Cash', ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, returnData.invoiceNo);
            stmt.setInt(2, returnData.cashierId);
            stmt.setInt(3, returnData.supervisorId);
            stmt.setBigDecimal(4, returnData.refundTotal);
            stmt.setString(5, returnData.notes);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return -1;
            }
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }
    
    /**
     * Insert all return items using batch processing for performance
     */
    private static void insertReturnItems(Connection conn, int returnId, List<ReturnItemData> returnItems) 
            throws SQLException {
        String sql = "INSERT INTO pos_return_items (return_id, invoice_item_id, online_inventory_item_id, " +
                    "in_store_inventory_id, qty_returned, refund_amount) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (ReturnItemData item : returnItems) {
                stmt.setInt(1, returnId);
                stmt.setInt(2, item.invoiceItemId);
                
                // Handle nullable foreign keys
                if (item.onlineInventoryItemId > 0) {
                    stmt.setInt(3, item.onlineInventoryItemId);
                } else {
                    stmt.setNull(3, java.sql.Types.INTEGER);
                }
                
                if (item.inStoreInventoryId > 0) {
                    stmt.setInt(4, item.inStoreInventoryId);
                } else {
                    stmt.setNull(4, java.sql.Types.INTEGER);
                }
                
                stmt.setInt(5, item.qtyReturned);
                stmt.setBigDecimal(6, item.refundAmount);
                
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            
            // Verify all inserts succeeded
            for (int result : results) {
                if (result == PreparedStatement.EXECUTE_FAILED) {
                    throw new SQLException("Failed to insert return item");
                }
            }
        }
    }
    
    /**
     * Update inventory quantities using batch processing
     * Increases stock for both online and in-store inventory
     */
    private static void updateInventoryQuantities(Connection conn, List<ReturnItemData> returnItems) 
            throws SQLException {
        
        // Batch update for online inventory
        String onlineUpdateSql = "UPDATE online_product_variant SET quantity = quantity + ? WHERE id = ?";
        
        // Batch update for in-store inventory  
        String inStoreUpdateSql = "UPDATE in_store_product_details SET quantity = quantity + ? WHERE id = ?";
        
        try (PreparedStatement onlineStmt = conn.prepareStatement(onlineUpdateSql);
             PreparedStatement inStoreStmt = conn.prepareStatement(inStoreUpdateSql)) {
            
            // Prepare online inventory updates
            for (ReturnItemData item : returnItems) {
                if (item.onlineInventoryItemId > 0) {
                    onlineStmt.setInt(1, item.qtyReturned);
                    onlineStmt.setInt(2, item.onlineInventoryItemId);
                    onlineStmt.addBatch();
                }
                
                if (item.inStoreInventoryId > 0) {
                    inStoreStmt.setInt(1, item.qtyReturned);
                    inStoreStmt.setInt(2, item.inStoreInventoryId);
                    inStoreStmt.addBatch();
                }
            }
            
            // Execute batch updates
            onlineStmt.executeBatch();
            inStoreStmt.executeBatch();
        }
    }
    
    /**
     * Log supervisor authorization activity
     */
    private static void logReturnAuthorization(Connection conn, int supervisorId, String invoiceNo, 
                                             BigDecimal refundTotal, int cashierId) throws SQLException {
        String sql = "INSERT INTO activity_log (staff_id, activity_type, details) VALUES (?, ?, ?)";
        
        String details = String.format(
            "Authorized return for Invoice #%s - Refund: ₱%.2f - Processed by Cashier ID: %d",
            invoiceNo, refundTotal, cashierId
        );
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supervisorId);
            stmt.setString(2, "return_authorization");
            stmt.setString(3, details);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Log cashier return processing activity
     */
    private static void logReturnProcessing(Connection conn, int cashierId, String invoiceNo, 
                                          BigDecimal refundTotal, int supervisorId) throws SQLException {
        String sql = "INSERT INTO activity_log (staff_id, activity_type, details) VALUES (?, ?, ?)";
        
        String details = String.format(
            "Processed return for Invoice #%s - Refund: ₱%.2f - Authorized by Supervisor ID: %d",
            invoiceNo, refundTotal, supervisorId
        );
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cashierId);
            stmt.setString(2, "returns_refunds");
            stmt.setString(3, details);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Validate return transaction data before processing
     * @param returnData Transaction data to validate
     * @return Validation error message, or null if valid
     */
    public static String validateReturnData(ReturnTransactionData returnData) {
        if (returnData.invoiceNo == null || returnData.invoiceNo.trim().isEmpty()) {
            return "Invoice number is required";
        }
        
        if (returnData.cashierId <= 0) {
            return "Valid cashier ID is required";
        }
        
        if (returnData.supervisorId <= 0) {
            return "Valid supervisor ID is required";
        }
        
        if (returnData.refundTotal == null || returnData.refundTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return "Refund total must be greater than zero";
        }
        
        if (returnData.returnItems == null || returnData.returnItems.isEmpty()) {
            return "At least one return item is required";
        }
        
        // Validate each return item
        for (ReturnItemData item : returnData.returnItems) {
            if (item.invoiceItemId <= 0) {
                return "Valid invoice item ID is required for all return items";
            }
            
            if (item.qtyReturned <= 0) {
                return "Return quantity must be greater than zero";
            }
            
            if (item.refundAmount == null || item.refundAmount.compareTo(BigDecimal.ZERO) < 0) {
                return "Refund amount must be non-negative";
            }
            
            // At least one inventory reference should be present
            if (item.onlineInventoryItemId <= 0 && item.inStoreInventoryId <= 0) {
                return "Each return item must reference either online or in-store inventory";
            }
        }
        
        return null; // Valid
    }
    
    /**
     * Check if invoice exists and get basic info
     * @param invoiceNo Invoice number to check
     * @return Map with invoice info (exists, total, date) or null if not found
     */
    public static Map<String, Object> getInvoiceInfo(String invoiceNo) throws SQLException {
        String sql = "SELECT COUNT(*) as exists, COALESCE(SUM(total_amount), 0) as total_amount, " +
                    "MAX(transaction_date) as transaction_date FROM pos_transactions WHERE invoice_no = ?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, invoiceNo);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt("exists") > 0) {
                    return Map.of(
                        "exists", true,
                        "totalAmount", rs.getBigDecimal("total_amount"),
                        "transactionDate", rs.getTimestamp("transaction_date")
                    );
                }
            }
        }
        
        return Map.of("exists", false);
    }
}