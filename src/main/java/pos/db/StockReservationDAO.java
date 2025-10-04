package pos.db;

import pos.model.StockReservation;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimized Data Access Object for managing stock reservations using transaction_id.
 * All operations are designed for maximum performance with minimal database queries.
 */
public class StockReservationDAO {
    
    /**
     * Result class for stock availability check
     */
    public static class StockAvailability {
        public final boolean isAvailable;
        public final int totalStock;
        public final int reservedQuantity;
        public final int availableStock;
        public final String message;
        
        public StockAvailability(boolean isAvailable, int totalStock, int reservedQuantity, 
                               int availableStock, String message) {
            this.isAvailable = isAvailable;
            this.totalStock = totalStock;
            this.reservedQuantity = reservedQuantity;
            this.availableStock = availableStock;
            this.message = message;
        }
        
        public static StockAvailability available(int totalStock, int reservedQuantity, int availableStock) {
            return new StockAvailability(true, totalStock, reservedQuantity, availableStock, "Stock available");
        }
        
        public static StockAvailability unavailable(int totalStock, int reservedQuantity, 
                                                   int availableStock, String message) {
            return new StockAvailability(false, totalStock, reservedQuantity, availableStock, message);
        }
    }
    
    /**
     * OPTIMIZED: Check available stock with a single efficient query.
     * Uses excluded transaction_id to avoid checking own cart's reservations.
     * 
     * @param conn Database connection
     * @param sku Product SKU
     * @param requestedQty Quantity requested to add to cart
     * @param excludeTransactionId Transaction ID to exclude (current cart's transaction)
     * @return StockAvailability with details about stock status
     */
    public static StockAvailability checkAvailableStock(Connection conn, String sku, int requestedQty, 
                                                       String excludeTransactionId) throws SQLException {
        // Get inventory item info
        ProductDAO.InventoryItemInfo itemInfo = ProductDAO.getInventoryItemInfoBySku(conn, sku);
        if (itemInfo == null) {
            return StockAvailability.unavailable(0, 0, 0, "Product not found");
        }
        
        String table = ("both".equalsIgnoreCase(itemInfo.saleChannel) || "online".equalsIgnoreCase(itemInfo.saleChannel)) 
                       ? "online_product_variant" 
                       : "in_store_product_details";
        
        String columnName = ("both".equalsIgnoreCase(itemInfo.saleChannel) || "online".equalsIgnoreCase(itemInfo.saleChannel)) 
                          ? "online_inventory_item_id" 
                          : "in_store_inventory_id";
        
        // Single optimized query that gets both stock and reserved quantity
        String sql = "SELECT " +
                    "(SELECT quantity FROM " + table + " WHERE id = ?) AS total_stock, " +
                    "(SELECT COALESCE(SUM(quantity), 0) FROM stock_reservations " +
                    " WHERE " + columnName + " = ? AND expires_at > NOW() " +
                    (excludeTransactionId != null ? " AND transaction_id != ?" : "") +
                    ") AS reserved_qty";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, itemInfo.inventoryItemId);
            stmt.setInt(2, itemInfo.inventoryItemId);
            if (excludeTransactionId != null) {
                stmt.setString(3, excludeTransactionId);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int totalStock = rs.getInt("total_stock");
                    int reservedQuantity = rs.getInt("reserved_qty");
                    int availableStock = totalStock - reservedQuantity;
                    
                    if (availableStock >= requestedQty) {
                        return StockAvailability.available(totalStock, reservedQuantity, availableStock);
                    } else {
                        String message = String.format("Insufficient stock. Available: %d, Requested: %d", 
                                                     availableStock, requestedQty);
                        return StockAvailability.unavailable(totalStock, reservedQuantity, availableStock, message);
                    }
                }
            }
        }
        
        return StockAvailability.unavailable(0, 0, 0, "Failed to check stock");
    }
    
    /**
     * Create or update reservation with the specified quantity.
     * If reservation exists: SET quantity to new value and UPDATE expires_at
     * If reservation doesn't exist: INSERT new reservation
     * 
     * @param conn Database connection
     * @param transactionId Unique transaction ID for this cart
     * @param sku Product SKU
     * @param quantity Total quantity to reserve (not increment amount)
     * @return true if successful
     */
    public static boolean upsertReservation(Connection conn, String transactionId, String sku, int quantity) 
            throws SQLException {
        // Get inventory item info
        ProductDAO.InventoryItemInfo itemInfo = ProductDAO.getInventoryItemInfoBySku(conn, sku);
        if (itemInfo == null) {
            throw new SQLException("Product not found: " + sku);
        }
        
        // Check if stock is available (excluding own transaction)
        StockAvailability availability = checkAvailableStock(conn, sku, quantity, transactionId);
        if (!availability.isAvailable) {
            throw new SQLException(availability.message);
        }
        
        // Prepare reservation data
        Integer onlineId = null;
        Integer inStoreId = null;
        if ("both".equalsIgnoreCase(itemInfo.saleChannel) || "online".equalsIgnoreCase(itemInfo.saleChannel)) {
            onlineId = itemInfo.inventoryItemId;
        } else {
            inStoreId = itemInfo.inventoryItemId;
        }
        
        String columnName = onlineId != null ? "online_inventory_item_id" : "in_store_inventory_id";
        Integer itemId = onlineId != null ? onlineId : inStoreId;
        
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + (15 * 60 * 1000));
        
        // First, try to UPDATE existing reservation (set to new total quantity)
        String updateSql = "UPDATE stock_reservations " +
                          "SET quantity = ?, expires_at = ? " +
                          "WHERE transaction_id = ? AND " + columnName + " = ?";
        
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setInt(1, quantity);
            updateStmt.setTimestamp(2, expiresAt);
            updateStmt.setString(3, transactionId);
            updateStmt.setInt(4, itemId);
            
            int rowsUpdated = updateStmt.executeUpdate();
            
            // If UPDATE affected rows, we're done
            if (rowsUpdated > 0) {
                return true;
            }
        }
        
        // If UPDATE didn't affect any rows, INSERT new reservation
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String insertSql = "INSERT INTO stock_reservations " +
                          "(transaction_id, online_inventory_item_id, in_store_inventory_id, quantity, channel, reserved_at, expires_at) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, transactionId);
            insertStmt.setObject(2, onlineId);
            insertStmt.setObject(3, inStoreId);
            insertStmt.setInt(4, quantity);
            insertStmt.setString(5, itemInfo.saleChannel);
            insertStmt.setTimestamp(6, now);
            insertStmt.setTimestamp(7, expiresAt);
            
            return insertStmt.executeUpdate() > 0;
        }
    }
    
    /**
     * OPTIMIZED: Remove a specific item's reservation for a transaction.
     * Single query deletion by transaction_id and inventory_item_id.
     * 
     * @param conn Database connection
     * @param transactionId Transaction ID
     * @param sku Product SKU
     * @return Number of reservations removed
     */
    public static int removeReservation(Connection conn, String transactionId, String sku) 
            throws SQLException {
        ProductDAO.InventoryItemInfo itemInfo = ProductDAO.getInventoryItemInfoBySku(conn, sku);
        if (itemInfo == null) {
            return 0;
        }
        
        String columnName = ("both".equalsIgnoreCase(itemInfo.saleChannel) || "online".equalsIgnoreCase(itemInfo.saleChannel)) 
                          ? "online_inventory_item_id" 
                          : "in_store_inventory_id";
        
        String sql = "DELETE FROM stock_reservations " +
                    "WHERE transaction_id = ? AND " + columnName + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);
            stmt.setInt(2, itemInfo.inventoryItemId);
            
            return stmt.executeUpdate();
        }
    }
    
    /**
     * OPTIMIZED: Clear all reservations for a transaction after successful checkout.
     * Single query deletion by transaction_id.
     * 
     * @param conn Database connection
     * @param transactionId Transaction ID to clear
     * @return Number of reservations cleared
     */
    public static int clearReservationsByTransaction(Connection conn, String transactionId) 
            throws SQLException {
        String sql = "DELETE FROM stock_reservations WHERE transaction_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);
            return stmt.executeUpdate();
        }
    }
    
    /**
     * OPTIMIZED: Batch clear multiple transactions at once (for checkout with multiple cart items).
     * 
     * @param conn Database connection
     * @param transactionIds List of transaction IDs to clear
     * @return Number of reservations cleared
     */
    public static int clearReservationsByTransactions(Connection conn, List<String> transactionIds) 
            throws SQLException {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return 0;
        }
        
        // If only one transaction, use simpler method
        if (transactionIds.size() == 1) {
            return clearReservationsByTransaction(conn, transactionIds.get(0));
        }
        
        // Build IN clause for multiple transactions
        StringBuilder sql = new StringBuilder("DELETE FROM stock_reservations WHERE transaction_id IN (");
        for (int i = 0; i < transactionIds.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(")");
        
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < transactionIds.size(); i++) {
                stmt.setString(i + 1, transactionIds.get(i));
            }
            return stmt.executeUpdate();
        }
    }
    
    /**
     * Clean up expired reservations.
     * Should be called periodically to free up reserved stock.
     * 
     * @param conn Database connection
     * @return Number of expired reservations cleaned up
     */
    public static int cleanupExpiredReservations(Connection conn) throws SQLException {
        String sql = "DELETE FROM stock_reservations WHERE expires_at <= NOW()";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("Cleaned up " + deleted + " expired reservations");
            }
            return deleted;
        }
    }
    
    /**
     * Get all active reservations for a specific transaction
     */
    public static List<StockReservation> getReservationsByTransaction(Connection conn, String transactionId) 
            throws SQLException {
        String sql = "SELECT * FROM stock_reservations " +
                    "WHERE transaction_id = ? AND expires_at > NOW()";
        
        List<StockReservation> reservations = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reservations.add(mapResultSetToReservation(rs));
                }
            }
        }
        
        return reservations;
    }
    
    /**
     * Helper method to map ResultSet to StockReservation object
     */
    private static StockReservation mapResultSetToReservation(ResultSet rs) throws SQLException {
        StockReservation reservation = new StockReservation();
        reservation.setReservationId(rs.getLong("reservation_id"));
        reservation.setTransactionId(rs.getString("transaction_id"));
        
        // Handle nullable foreign keys
        int onlineId = rs.getInt("online_inventory_item_id");
        reservation.setOnlineInventoryItemId(rs.wasNull() ? null : onlineId);
        
        int inStoreId = rs.getInt("in_store_inventory_id");
        reservation.setInStoreInventoryId(rs.wasNull() ? null : inStoreId);
        
        reservation.setQuantity(rs.getInt("quantity"));
        reservation.setChannel(rs.getString("channel"));
        reservation.setReservedAt(rs.getTimestamp("reserved_at"));
        reservation.setExpiresAt(rs.getTimestamp("expires_at"));
        
        return reservation;
    }
}
