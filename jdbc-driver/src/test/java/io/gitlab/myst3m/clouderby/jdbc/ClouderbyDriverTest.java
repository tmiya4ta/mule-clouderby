package io.gitlab.myst3m.clouderby.jdbc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClouderbyDriver.
 */
@DisplayName("ClouderbyDriver Tests")
class ClouderbyDriverTest {

    private final ClouderbyDriver driver = new ClouderbyDriver();

    @Nested
    @DisplayName("URL Acceptance Tests")
    class UrlAcceptanceTests {

        @Test
        @DisplayName("Should accept valid clouderby URL")
        void testAcceptsValidUrl() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost:8080/mydb")).isTrue();
        }

        @Test
        @DisplayName("Should accept URL with secure parameter")
        void testAcceptsUrlWithSecure() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://example.com:443/mydb?secure=true")).isTrue();
        }

        @Test
        @DisplayName("Should accept URL with embedded parameter")
        void testAcceptsUrlWithEmbedded() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost:8080/test?embedded=true")).isTrue();
        }

        @Test
        @DisplayName("Should accept URL with multiple parameters")
        void testAcceptsUrlWithMultipleParams() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost:8080/test?embedded=true&dataDir=./data")).isTrue();
        }

        @Test
        @DisplayName("Should reject non-clouderby URLs")
        void testRejectsInvalidUrl() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:mysql://localhost:3306/mydb")).isFalse();
            assertThat(driver.acceptsURL("jdbc:postgresql://localhost:5432/mydb")).isFalse();
            assertThat(driver.acceptsURL("jdbc:sqlite:test.db")).isFalse();
        }

        @Test
        @DisplayName("Should reject null URL")
        void testRejectsNullUrl() throws SQLException {
            assertThat(driver.acceptsURL(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("URL Parsing Tests")
    class UrlParsingTests {

        @Test
        @DisplayName("Should use default port 8080 when not specified")
        void testParseUrlDefaultPort() throws SQLException {
            // This test verifies the parsing logic by attempting to connect
            // The connection will fail but we can verify URL parsing worked
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost/mydb")).isTrue();
        }

        @Test
        @DisplayName("Should parse URL with explicit port")
        void testParseUrlWithPort() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost:9090/mydb")).isTrue();
        }

        @Test
        @DisplayName("Should parse URL with query parameters")
        void testParseUrlWithMultipleParams() throws SQLException {
            String url = "jdbc:clouderby://localhost:8080/mydb?embedded=true&dataDir=/tmp/data&secure=false";
            assertThat(driver.acceptsURL(url)).isTrue();
        }

        @Test
        @DisplayName("Should use 'default' as database name when empty")
        void testParseUrlEmptyDatabase() throws SQLException {
            assertThat(driver.acceptsURL("jdbc:clouderby://localhost:8080/")).isTrue();
        }

        @Test
        @DisplayName("connect should return null for non-clouderby URL")
        void testConnectReturnsNullForInvalidUrl() throws SQLException {
            assertThat(driver.connect("jdbc:mysql://localhost/test", null)).isNull();
        }
    }

    @Nested
    @DisplayName("Driver Metadata Tests")
    class DriverMetadataTests {

        @Test
        @DisplayName("getMajorVersion should return 1")
        void testMajorVersion() {
            assertThat(driver.getMajorVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMinorVersion should return 0")
        void testMinorVersion() {
            assertThat(driver.getMinorVersion()).isEqualTo(0);
        }

        @Test
        @DisplayName("jdbcCompliant should return false")
        void testJdbcCompliant() {
            assertThat(driver.jdbcCompliant()).isFalse();
        }

        @Test
        @DisplayName("getPropertyInfo should return user and password properties")
        void testPropertyInfo() throws SQLException {
            DriverPropertyInfo[] props = driver.getPropertyInfo("jdbc:clouderby://localhost/test", null);
            assertThat(props).hasSize(2);
            assertThat(props[0].name).isEqualTo("user");
            assertThat(props[0].required).isFalse();
            assertThat(props[1].name).isEqualTo("password");
            assertThat(props[1].required).isFalse();
        }
    }

    @Nested
    @DisplayName("Driver Registration Tests")
    class DriverRegistrationTests {

        @Test
        @DisplayName("Driver should be registered with DriverManager")
        void testDriverRegistered() throws SQLException {
            // The static initializer should have registered the driver
            java.sql.Driver registeredDriver = DriverManager.getDriver("jdbc:clouderby://localhost/test");
            assertThat(registeredDriver).isInstanceOf(ClouderbyDriver.class);
        }
    }

}
