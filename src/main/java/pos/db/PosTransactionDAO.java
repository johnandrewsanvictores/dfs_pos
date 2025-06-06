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

    // Insert a new record into transaction_log
    public static void insertTransactionLog(
        Connection conn,
        String transactionId,
        Integer onlineOrderId,
        Integer posTransactionId,
        Integer returnId,
        String channel,
        String type,
        String status
    ) throws SQLException {
        String sql = "INSERT INTO transaction_log (transaction_id, online_order_id, pos_transaction_id, return_id, channel, type, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, transactionId);
        if (onlineOrderId != null) stmt.setInt(2, onlineOrderId); else stmt.setNull(2, java.sql.Types.INTEGER);
        if (posTransactionId != null) stmt.setInt(3, posTransactionId); else stmt.setNull(3, java.sql.Types.INTEGER);
        if (returnId != null) stmt.setInt(4, returnId); else stmt.setNull(4, java.sql.Types.INTEGER);
        stmt.setString(5, channel);
        stmt.setString(6, type);
        stmt.setString(7, status);
        stmt.executeUpdate();
        stmt.close();
    }

    // Generate the next transaction_id in the format TRX-YYYYMMDD-00001
    public static String generateNextTransactionId(Connection conn) throws SQLException {
        String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String prefix = "TRX-" + today + "-";
        String sql = "SELECT transaction_id FROM transaction_log WHERE transaction_id LIKE ? ORDER BY transaction_id DESC LIMIT 1";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, prefix + "%");
        ResultSet rs = stmt.executeQuery();
        int nextSeq = 1;
        if (rs.next()) {
            String lastId = rs.getString("transaction_id");
            String[] parts = lastId.split("-");
            if (parts.length == 3) {
                try {
                    nextSeq = Integer.parseInt(parts[2]) + 1;
                } catch (Exception ignored) {}
            }
        }
        rs.close();
        stmt.close();
        return String.format("%s%05d", prefix, nextSeq);
    }
} 