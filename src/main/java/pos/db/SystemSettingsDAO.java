package pos.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class SystemSettingsDAO {
    public static Map<String, String> getSettingsByGroup(String groupName) throws SQLException {
        Connection conn = DBConnection.getConnection();
        String sql = "SELECT variable_name, value FROM system_settings WHERE group_name = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, groupName);
        ResultSet rs = stmt.executeQuery();
        Map<String, String> settings = new HashMap<>();
        while (rs.next()) {
            settings.put(rs.getString("variable_name"), rs.getString("value"));
        }
        rs.close();
        stmt.close();
        return settings;
    }

    public static int getVatRate() throws SQLException {
        Map<String, String> vatSettings = getSettingsByGroup("vat_settings");
        String enabled = vatSettings.getOrDefault("vat_enabled", "0");
        if (!"1".equals(enabled)) return 0;
        try {
            return Integer.parseInt(vatSettings.getOrDefault("vat_rate", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isVatEnabled() throws SQLException {
        Map<String, String> vatSettings = getSettingsByGroup("vat_settings");
        return "1".equals(vatSettings.getOrDefault("vat_enabled", "0"));
    }

    public static Map<String, String> getBusinessInfo() throws SQLException {
        return getSettingsByGroup("business_info");
    }
} 