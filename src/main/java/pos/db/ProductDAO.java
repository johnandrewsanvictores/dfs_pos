package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
                     "FROM " +
                     "inventory i " +
                     "JOIN in_store_product_details d ON i.id = d.inventory_product_id " +
                     "WHERE " +
                     "i.product_status = 'active' " +
                     "AND i.sale_channel = 'in-store' " +
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
                     "FROM " +
                     "inventory i " +
                     "JOIN online_product_details opd ON i.id = opd.product_id " +
                     "JOIN online_product_variant opv ON opd.id = opv.online_product_id " +
                     "WHERE " +
                     "i.product_status = 'active' " +
                     "AND i.sale_channel = 'both';";
        PreparedStatement stmt = conn.prepareStatement(sql);
        return stmt.executeQuery();
    }
} 