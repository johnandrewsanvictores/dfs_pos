package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    public static ResultSet getAllActiveProducts() throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT " +
                     "i.id AS inventory_id, " +
                     "i.description, " +
                     "i.item_name, " +
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

    // Get inventory_id and sale_channel by SKU
    public static InventoryInfo getInventoryInfoBySku(String sku) throws SQLException {
        Connection conn = DBConnection.getConnection();
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
        conn.close();
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

    public static InventoryItemInfo getInventoryItemInfoBySku(String sku) throws SQLException {
        Connection conn = DBConnection.getConnection();
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
        conn.close();
        return info;
    }
    /**
     * Decrease the quantity of multiple products by SKU and sale channel in a batch using the IN keyword.
     * @param skuToQty Map of SKU to quantity to decrease.
     * @param saleChannel The sale channel ("in-store", "both", or "online").
     * @throws SQLException if a database error occurs.
     */
    public static void decreaseProductQuantitiesBatch(Map<String, Integer> skuToQty, String saleChannel) throws SQLException {
        if (skuToQty == null || skuToQty.isEmpty()) return;
        Connection conn = DBConnection.getConnection();
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
        conn.close();
    }
} 