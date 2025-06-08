package pos.db;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class PromotionDao {
    public static class Promotion {
        public int id;
        public String title;
        public String type; // percentage, fixed, free_shipping
        public double value;
        public double minPurchase;
        public String saleChannel;
        public LocalDate activationDate, expirationDate;
        public String appliesToType; // all, category, product
        public Integer appliesToId; // null for all
    }

    // Fetch all active automatic discounts for in-store or both
    public static List<Promotion> getActiveAutomaticDiscounts(Connection conn) throws SQLException {
        String sql = "SELECT v.*, va.applies_to_type, va.applies_to_id " +
                     "FROM vouchers v " +
                     "JOIN voucher_applicability va ON v.id = va.voucher_id " +
                     "WHERE v.application_method = 'automatic_discount' " +
                     "AND (v.sale_channel = 'in-store' OR v.sale_channel = 'both') " +
                     "AND v.activation_date <= CURDATE() AND v.expiration_date >= CURDATE()";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();
        List<Promotion> list = new ArrayList<>();
        while (rs.next()) {
            Promotion p = new Promotion();
            p.id = rs.getInt("id");
            p.title = rs.getString("title");
            p.type = rs.getString("type");
            p.value = rs.getDouble("value");
            p.minPurchase = rs.getDouble("min_purchase");
            p.saleChannel = rs.getString("sale_channel");
            p.activationDate = rs.getDate("activation_date").toLocalDate();
            p.expirationDate = rs.getDate("expiration_date").toLocalDate();
            p.appliesToType = rs.getString("applies_to_type");
            p.appliesToId = rs.getObject("applies_to_id") == null ? null : rs.getInt("applies_to_id");
            list.add(p);
        }
        rs.close();
        stmt.close();
        return list;
    }

    // Find the best applicable promotion for a cart item
    public static Promotion getBestPromotionForItem(Connection conn, String sku, int categoryId, double price, int quantity, List<Promotion> promos) {
        Promotion best = null;
        double maxDiscount = 0;
        double lineTotal = price * quantity;
        for (Promotion p : promos) {
            boolean applies = false;
            if ("all".equals(p.appliesToType)) {
                applies = true;
            } else if ("category".equals(p.appliesToType) && p.appliesToId != null && p.appliesToId == categoryId) {
                applies = true;
            } else if ("product".equals(p.appliesToType) && p.appliesToId != null && p.appliesToId.toString().equals(sku)) {
                applies = true;
            }
            if (!applies) continue;
            if (lineTotal < p.minPurchase) continue;
            double discount = 0;
            if ("percentage".equals(p.type)) {
                discount = lineTotal * (p.value / 100.0);
            } else if ("fixed".equals(p.type)) {
                discount = Math.min(p.value, lineTotal);
            }
            if (discount > maxDiscount) {
                maxDiscount = discount;
                best = p;
            }
        }
        return best;
    }
} 