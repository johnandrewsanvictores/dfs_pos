package pos.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = DBCredentials.URL;
    private static final String USER = DBCredentials.USER;
    private static final String PASSWORD = DBCredentials.PASSWORD;

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}