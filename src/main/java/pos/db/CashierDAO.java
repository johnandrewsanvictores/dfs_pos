package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CashierDAO {
    // Fetch cashier by username, only if role is 'cashier' and status is 'active'
    public static ResultSet getActiveCashierByUsername(String username) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT * FROM staff_acc WHERE username = ? AND role = 'cashier' AND status = 'active'";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, username);
        return stmt.executeQuery();
    }
    // Add more methods for cashier authentication, update, etc. here
    public static void updateLastLogin(String username) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "UPDATE staff_acc SET last_login = NOW() WHERE username = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, username);
        stmt.executeUpdate();
        stmt.close();
        conn.close();
    }
    public static int getStaffIdByUsername(String username) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT id FROM staff_acc WHERE username = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, username);
        ResultSet rs = stmt.executeQuery();
        int staffId = -1;
        if (rs.next()) {
            staffId = rs.getInt("id");
        }
        rs.close();
        stmt.close();
        conn.close();
        return staffId;
    }
    public static void logActivity(int staffId, String activityType, String details) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "INSERT INTO activity_log (staff_id, activity_type, details) VALUES (?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, staffId);
        stmt.setString(2, activityType);
        stmt.setString(3, details);
        stmt.executeUpdate();
        stmt.close();
        conn.close();
    }
} 