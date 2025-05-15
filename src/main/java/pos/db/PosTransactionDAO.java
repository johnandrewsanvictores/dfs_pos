package pos.db;

import java.sql.*;
import java.util.List;
import java.util.Map;

public class PosTransactionDAO {
    // Generate the next invoice number in the format 1-X
    public static String generateNextInvoiceNo(Connection conn) throws SQLException {
        String sql = "SELECT invoice_no FROM pos_transactions ORDER BY id DESC LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();
        int next = 1;
        if (rs.next()) {
            String last = rs.getString("invoice_no");
            try {
                next = Integer.parseInt(last) + 1;
            } catch (Exception ignored) {}
        }
        rs.close();
        stmt.close();
        return String.format("%07d", next); // 7 digits, leading zeros
    }

    // Insert a new POS transaction with payment_ref_no
    public static int insertPosTransaction(
        Connection conn,
        String invoiceNo, java.sql.Timestamp transactionDate, String paymentMethod, int staffId,
        double subtotal, double discount, double tax, double totalAmount, double receivedAmount,
        String paymentRefNo
    ) throws SQLException {
        String sql = "INSERT INTO pos_transactions (transaction_date, payment_method, staff_id, subtotal, discount, tax, total_amount, received_amount, invoice_no, payment_ref_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setTimestamp(1, transactionDate);
        stmt.setString(2, paymentMethod);
        stmt.setInt(3, staffId);
        stmt.setDouble(4, subtotal);
        stmt.setDouble(5, discount);
        stmt.setDouble(6, tax);
        stmt.setDouble(7, totalAmount);
        stmt.setDouble(8, receivedAmount);
        stmt.setString(9, invoiceNo);
        stmt.setString(10, paymentRefNo);
        int affectedRows = stmt.executeUpdate();
        int id = -1;
        if (affectedRows > 0) {
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) id = keys.getInt(1);
            keys.close();
        }
        stmt.close();
        return id;
    }

    // Insert items into physical_sale_items (with sale_channel and inventory_id)
    public static void insertPhysicalSaleItems(Connection conn, int posTransactionId, List<Map<String, Object>> items) throws SQLException {
        String sql = "INSERT INTO physical_sale_items (pos_transaction_id, sku, order_quantity, stock_quantity, subtotal, sale_channel, online_inventory_item_id, in_store_inventory_item_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);
        int batchSize = 100;
        int count = 0;
        for (Map<String, Object> item : items) {
            stmt.setInt(1, posTransactionId);
            stmt.setString(2, (String) item.get("sku"));
            stmt.setInt(3, (int) item.get("order_quantity"));
            stmt.setInt(4, (int) item.get("stock_quantity"));
            stmt.setDouble(5, (double) item.get("subtotal"));
            stmt.setString(6, (String) item.get("sale_channel"));
            if (item.get("online_inventory_item_id") != null) {
                stmt.setObject(7, item.get("online_inventory_item_id"), java.sql.Types.INTEGER);
            } else {
                stmt.setNull(7, java.sql.Types.INTEGER);
            }
            if (item.get("in_store_inventory_item_id") != null) {
                stmt.setObject(8, item.get("in_store_inventory_item_id"), java.sql.Types.INTEGER);
            } else {
                stmt.setNull(8, java.sql.Types.INTEGER);
            }
            stmt.addBatch();
            count++;
            if (count % batchSize == 0) {
                stmt.executeBatch();
            }
        }
        stmt.executeBatch(); // execute remaining
        stmt.close();
    }
} 