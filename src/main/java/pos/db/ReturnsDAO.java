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
     * Process complete return transaction atomically
     * @param returnData Complete return transaction data
     * @return Return ID if successful, -1 if failed
     * @throws SQLException if database operation fails
     */
    public static int processReturnTransaction(ReturnTransactionData returnData) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // 1. Insert main return record
            int returnId = insertReturn(conn, returnData);
            if (returnId == -1) {
                throw new SQLException("Failed to insert return record");
            }
            
            // 2. Insert all return items
            insertReturnItems(conn, returnId, returnData.returnItems);
            
            // 3. Update inventory quantities (increase stock)
            updateInventoryQuantities(conn, returnData.returnItems);
            
            // 4. Log supervisor authorization activity
            logReturnAuthorization(conn, returnData.supervisorId, returnData.invoiceNo, 
                                 returnData.refundTotal, returnData.cashierId);
            
            // 5. Log cashier return processing activity
            logReturnProcessing(conn, returnData.cashierId, returnData.invoiceNo, 
                              returnData.refundTotal, returnData.supervisorId);
            
            conn.commit(); // All operations successful
            return returnId;
            
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
     * Insert main return record
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
        String inStoreUpdateSql = "UPDATE in_store_product_variant SET quantity = quantity + ? WHERE id = ?";
        
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