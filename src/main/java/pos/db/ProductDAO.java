package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;

public class ProductDAO {
    // Fetch active in-store products for POS
    public static ResultSet getActiveInStoreProducts() throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT i.id AS inventory_id, i.description, i.item_name, d.* " +
                     "FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE i.product_status = 'active' AND i.sale_channel = 'in-store'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        return stmt.executeQuery();
    }

    // Fetch active 'both' (online) products for POS
    public static ResultSet getActiveBothChannelProducts() throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT i.id AS inventory_id, i.description, i.item_name, " +
                     "opd.id AS online_product_details_id, opd.product_id AS online_product_details_product_id, opv.* " +
                     "FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE i.product_status = 'active' AND i.sale_channel = 'both'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        return stmt.executeQuery();
    }

    // Fetch all active products for POS
    public static ResultSet getAllActiveProductsRaw() throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "d.sku, " +
                     "d.unit_price, " +
                     "d.quantity, " +
                     "NULL AS online_product_id, " +
                     "NULL AS image_path " +
                     "FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE i.product_status = 'active' AND (i.sale_channel = 'in-store' OR i.sale_channel = 'both') " +
                     "UNION ALL " +
                     "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "opv.sku, " +
                     "opv.unit_price, " +
                     "opv.quantity, " +
                     "opv.online_product_id, " +
                     "opv.image_path " +
                     "FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE i.product_status = 'active' AND i.sale_channel = 'both'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        return stmt.executeQuery();
    }

    // Efficiently fetch all active products as Product objects with categoryId
    public static java.util.List<pos.model.Product> getAllActiveProductsAsList() throws SQLException {
        java.util.List<pos.model.Product> products = new java.util.ArrayList<>();
        try (ResultSet rs = getAllActiveProductsRaw()) {
            while (rs.next()) {
                String sku = rs.getString("sku");
                double price = rs.getDouble("unit_price");
                String description = rs.getString("description");
                String imagePath = rs.getString("image_path");
                int quantity = rs.getInt("quantity");
                int categoryId = rs.getInt("category_id");
                products.add(new pos.model.Product(sku, price, description, imagePath, quantity, categoryId));
            }
        }
        return products;
    }

    // Get inventory_id and sale_channel by SKU
    public static InventoryInfo getInventoryInfoBySku(Connection conn, String sku) throws SQLException {
        String sql = "SELECT i.id AS inventory_id, i.sale_channel FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id WHERE d.sku = ? " +
                     "UNION ALL " +
                     "SELECT i.id AS inventory_id, i.sale_channel FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id WHERE opv.sku = ? ";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, sku);
        stmt.setString(2, sku);
        ResultSet rs = stmt.executeQuery();
        InventoryInfo info = null;
        if (rs.next()) {
            info = new InventoryInfo(rs.getInt("inventory_id"), rs.getString("sale_channel"));
        }
        rs.close();
        stmt.close();
        return info;
    }

    public static class InventoryInfo {
        public final int inventoryId;
        public final String saleChannel;
        public InventoryInfo(int inventoryId, String saleChannel) {
            this.inventoryId = inventoryId;
            this.saleChannel = saleChannel;
        }
    }

    public static class InventoryItemInfo {
        public final int inventoryItemId; // This is in_store_product_details.id or online_product_variant.id
        public final String saleChannel;
        public InventoryItemInfo(int inventoryItemId, String saleChannel) {
            this.inventoryItemId = inventoryItemId;
            this.saleChannel = saleChannel;
        }
    }

    public static InventoryItemInfo getInventoryItemInfoBySku(Connection conn, String sku) throws SQLException {
        String sql = "SELECT d.id AS inventory_item_id, i.sale_channel " +
                     "FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE d.sku = ? " +
                     "UNION ALL " +
                     "SELECT opv.id AS inventory_item_id, i.sale_channel " +
                     "FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE opv.sku = ? ";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, sku);
        stmt.setString(2, sku);
        ResultSet rs = stmt.executeQuery();
        InventoryItemInfo info = null;
        if (rs.next()) {
            info = new InventoryItemInfo(rs.getInt("inventory_item_id"), rs.getString("sale_channel"));
        }
        rs.close();
        stmt.close();
        return info;
    }
    /**
     * Decrease the quantity of multiple products by SKU and sale channel in a batch using the IN keyword.
     * @param skuToQty Map of SKU to quantity to decrease.
     * @param saleChannel The sale channel ("in-store", "both", or "online").
     * @throws SQLException if a database error occurs.
     */
    public static void decreaseProductQuantitiesBatch(Connection conn, Map<String, Integer> skuToQty, String saleChannel) throws SQLException {
        if (skuToQty == null || skuToQty.isEmpty()) return;
        String table;
        if ("both".equalsIgnoreCase(saleChannel) || "online".equalsIgnoreCase(saleChannel)) {
            table = "online_product_variant";
        } else {
            table = "in_store_product_details";
        }
        // Build SQL: UPDATE ... SET quantity = CASE sku WHEN ... THEN ... END WHERE sku IN (...)
        StringBuilder sql = new StringBuilder("UPDATE " + table + " SET quantity = quantity - CASE sku ");
        for (String sku : skuToQty.keySet()) {
            sql.append(" WHEN ? THEN ?");
        }
        sql.append(" END WHERE sku IN (");
        sql.append(String.join(",", java.util.Collections.nCopies(skuToQty.size(), "?")));
        sql.append(")");
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            // Set WHEN ? THEN ? pairs
            for (Map.Entry<String, Integer> entry : skuToQty.entrySet()) {
                stmt.setString(idx++, entry.getKey());
                stmt.setInt(idx++, entry.getValue());
            }
            // Set IN (?) list
            for (String sku : skuToQty.keySet()) {
                stmt.setString(idx++, sku);
            }
            stmt.executeUpdate();
        }
    }

    public static void batchUpdateInventory(Connection conn, Map<String, Integer> skuToQty, String saleChannel) throws SQLException {
        int batchSize = 100;
        java.util.List<Map.Entry<String, Integer>> entries = new java.util.ArrayList<>(skuToQty.entrySet());
        for (int i = 0; i < entries.size(); i += batchSize) {
            Map<String, Integer> batch = new java.util.HashMap<>();
            for (int j = i; j < i + batchSize && j < entries.size(); j++) {
                batch.put(entries.get(j).getKey(), entries.get(j).getValue());
            }
            decreaseProductQuantitiesBatch(conn, batch, saleChannel);
        }
    }

    // Fetch category_id by SKU
    public static Integer getCategoryIdBySku(Connection conn, String sku) throws SQLException {
        String sql = "SELECT i.category_id FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id WHERE d.sku = ? " +
                     "UNION ALL " +
                     "SELECT i.category_id FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id WHERE opv.sku = ? ";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, sku);
        stmt.setString(2, sku);
        ResultSet rs = stmt.executeQuery();
        Integer categoryId = null;
        if (rs.next()) {
            categoryId = rs.getInt("category_id");
        }
        rs.close();
        stmt.close();
        return categoryId;
    }
    
    /**
     * Get products modified since a specific timestamp
     * Since the database doesn't have updated_at columns, we'll use a different approach:
     * Compare current quantities with the last known state to detect changes
     */
    public static java.util.List<pos.model.Product> getModifiedProductsSince(Timestamp lastCheck) throws SQLException {
        // For now, we'll return all products and let the POSView compare quantities
        // This is less efficient but works with the current database schema
        return getAllActiveProductsAsList();
    }
    
    /**
     * Get current database timestamp - with proper connection management
     */
    public static Timestamp getCurrentDatabaseTimestamp() throws SQLException {
        String sql = "SELECT CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getTimestamp(1);
            }
        }
        throw new SQLException("Could not get database timestamp");
    }
    
    /**
     * OPTIMIZED: Get products with their data hash for efficient change detection
     * Uses concatenated key fields to create a change signature
     */
    public static java.util.List<ProductWithStatus> getChangedProductsSince(Timestamp lastCheck) throws SQLException {
        // Create a hash-based comparison query that's efficient without timestamps
        String sql = "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "i.product_status, " +
                     "d.sku, " +
                     "d.unit_price, " +
                     "COALESCE(d.quantity, 0) AS quantity, " +
                     "NULL AS online_product_id, " +
                     "NULL AS image_path, " +
                     "CONCAT(d.sku, '|', d.unit_price, '|', COALESCE(d.quantity, 0), '|', i.product_status) AS data_hash " +
                     "FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE (i.sale_channel = 'in-store' OR i.sale_channel = 'both') " +
                     "UNION ALL " +
                     "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "i.product_status, " +
                     "opv.sku, " +
                     "opv.unit_price, " +
                     "COALESCE(opv.quantity, 0) AS quantity, " +
                     "opv.online_product_id, " +
                     "opv.image_path, " +
                     "CONCAT(opv.sku, '|', opv.unit_price, '|', COALESCE(opv.quantity, 0), '|', i.product_status) AS data_hash " +
                     "FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE i.sale_channel = 'both' " +
                     "ORDER BY sku";
        
        java.util.List<ProductWithStatus> products = new java.util.ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String sku = rs.getString("sku");
                double price = rs.getDouble("unit_price");
                String description = rs.getString("description");
                int categoryId = rs.getInt("category_id");
                String status = rs.getString("product_status");
                int quantity = rs.getInt("quantity");
                String imagePath = rs.getString("image_path");
                String dataHash = rs.getString("data_hash");
                
                ProductWithStatus product = new ProductWithStatus(sku, price, description, imagePath, 
                                                 quantity, categoryId, status);
                product.setDataHash(dataHash); // Store hash for comparison
                products.add(product);
            }
        } catch (SQLException e) {
            // Fallback to getting all products if hash query fails
            System.err.println("Hash-based query failed, falling back to full scan: " + e.getMessage());
            return getAllProductsWithStatus();
        }
        
        return products;
    }
    
    /**
     * FALLBACK: Get all products with status (only used when timestamp queries fail)
     */
    public static java.util.List<ProductWithStatus> getAllProductsWithStatus() throws SQLException {
        String sql = "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "i.product_status, " +
                     "d.sku, " +
                     "d.unit_price, " +
                     "COALESCE(d.quantity, 0) AS quantity, " +
                     "NULL AS online_product_id, " +
                     "NULL AS image_path " +
                     "FROM inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE (i.sale_channel = 'in-store' OR i.sale_channel = 'both') " +
                     "UNION ALL " +
                     "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
                     "i.category_id, " +
                     "i.product_status, " +
                     "opv.sku, " +
                     "opv.unit_price, " +
                     "COALESCE(opv.quantity, 0) AS quantity, " +
                     "opv.online_product_id, " +
                     "opv.image_path " +
                     "FROM inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE i.sale_channel = 'both' " +
                     "ORDER BY sku";
        
        java.util.List<ProductWithStatus> products = new java.util.ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String sku = rs.getString("sku");
                double price = rs.getDouble("unit_price");
                String description = rs.getString("description");
                int categoryId = rs.getInt("category_id");
                String status = rs.getString("product_status");
                int quantity = rs.getInt("quantity");
                String imagePath = rs.getString("image_path");
                
                products.add(new ProductWithStatus(sku, price, description, imagePath, 
                                                 quantity, categoryId, status));
            }
        }
        return products;
    }
    
    /**
     * Helper class to track product status for comprehensive change detection
     */
    public static class ProductWithStatus extends pos.model.Product {
        private final String status;
        private String dataHash; // For efficient change detection
        
        public ProductWithStatus(String sku, double price, String description, String imagePath, 
                               int quantity, int categoryId, String status) {
            super(sku, price, description, imagePath, quantity, categoryId);
            this.status = status;
        }
        
        public String getStatus() {
            return status;
        }
        
        public boolean isActive() {
            return "active".equalsIgnoreCase(status);
        }
        
        public boolean isArchived() {
            return "archived".equalsIgnoreCase(status) || "inactive".equalsIgnoreCase(status);
        }
        
        public void setDataHash(String dataHash) {
            this.dataHash = dataHash;
        }
        
        public String getDataHash() {
            return dataHash;
        }
        
        /**
         * Generate a hash from current product data for change detection
         */
        public String generateCurrentHash() {
            return getSku() + "|" + getPrice() + "|" + getQuantity() + "|" + status;
        }
    }
}