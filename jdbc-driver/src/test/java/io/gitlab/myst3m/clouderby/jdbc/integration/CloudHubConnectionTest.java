package io.gitlab.myst3m.clouderby.jdbc.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test JDBC connection to CloudHub deployed clouderby server.
 */
@DisplayName("CloudHub Connection Test")
class CloudHubConnectionTest {

    private static final String CLOUDHUB_URL = 
        "jdbc:clouderby://clouderby-mule-server-l2yy7y.pnwfdv.jpn-e1.cloudhub.io:443/testdb?secure=true";

    @Test
    @DisplayName("Should connect to CloudHub clouderby server")
    void testCloudHubConnection() throws SQLException {
        try (Connection conn = DriverManager.getConnection(CLOUDHUB_URL)) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
            System.out.println("Connected successfully!");
        }
    }

    @Test
    @DisplayName("Should execute CRUD operations on CloudHub")
    void testCloudHubCRUD() throws SQLException {
        try (Connection conn = DriverManager.getConnection(CLOUDHUB_URL)) {
            // Create table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS cloud_test");
                stmt.execute("CREATE TABLE cloud_test (id INTEGER PRIMARY KEY, name TEXT, value REAL)");
            }
            System.out.println("Table created");

            // Insert data
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cloud_test (id, name, value) VALUES (?, ?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "CloudHub Test");
                ps.setDouble(3, 3.14);
                int count = ps.executeUpdate();
                assertThat(count).isEqualTo(1);
            }
            System.out.println("Data inserted");

            // Query data
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM cloud_test")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("id")).isEqualTo(1);
                assertThat(rs.getString("name")).isEqualTo("CloudHub Test");
                assertThat(rs.getDouble("value")).isEqualTo(3.14);
                System.out.printf("Retrieved: id=%d, name=%s, value=%.2f%n",
                    rs.getInt("id"), rs.getString("name"), rs.getDouble("value"));
            }

            System.out.println("All CloudHub tests passed!");
        }
    }
}
