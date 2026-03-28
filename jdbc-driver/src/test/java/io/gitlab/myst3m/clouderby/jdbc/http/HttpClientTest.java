package io.gitlab.myst3m.clouderby.jdbc.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HttpClient.
 *
 * Note: These tests focus on URL construction and configuration.
 * Network-level tests require a mock HTTP server or integration tests.
 */
@DisplayName("HttpClient Tests")
class HttpClientTest {

    @Nested
    @DisplayName("URL Construction Tests")
    class UrlConstructionTests {

        @Test
        @DisplayName("HTTP URL should be constructed correctly")
        void testBaseUrl_Http() {
            HttpClient client = new HttpClient("localhost", 8080, false);
            assertThat(client.getBaseUrl()).isEqualTo("http://localhost:8080");
        }

        @Test
        @DisplayName("HTTPS URL should be constructed correctly")
        void testBaseUrl_Https() {
            HttpClient client = new HttpClient("example.com", 8443, true);
            assertThat(client.getBaseUrl()).isEqualTo("https://example.com:8443");
        }

        @Test
        @DisplayName("HTTPS on port 443 should omit port from URL")
        void testBaseUrl_HttpsDefaultPort() {
            HttpClient client = new HttpClient("example.com", 443, true);
            assertThat(client.getBaseUrl()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("HTTP on port 80 should include port in URL")
        void testBaseUrl_HttpPort80() {
            HttpClient client = new HttpClient("localhost", 80, false);
            assertThat(client.getBaseUrl()).isEqualTo("http://localhost:80");
        }

        @Test
        @DisplayName("Default constructor should use HTTP")
        void testDefaultConstructor() {
            HttpClient client = new HttpClient("localhost", 8080);
            assertThat(client.getBaseUrl()).isEqualTo("http://localhost:8080");
        }
    }

    @Nested
    @DisplayName("Session Management Tests")
    class SessionManagementTests {

        @Test
        @DisplayName("Session ID should be null initially")
        void testSessionIdInitiallyNull() {
            HttpClient client = new HttpClient("localhost", 8080);
            assertThat(client.getSessionId()).isNull();
        }

        @Test
        @DisplayName("Session ID should be settable")
        void testSetSessionId() {
            HttpClient client = new HttpClient("localhost", 8080);
            client.setSessionId("test-session-123");
            assertThat(client.getSessionId()).isEqualTo("test-session-123");
        }
    }

    @Nested
    @DisplayName("Protocol Message Tests")
    class ProtocolMessageTests {

        @Test
        @DisplayName("OpenRequest should serialize database name")
        void testOpenRequest() {
            Protocol.OpenRequest request = new Protocol.OpenRequest("mydb");
            assertThat(request.database).isEqualTo("mydb");
        }

        @Test
        @DisplayName("ExecuteRequest should have default fetch size")
        void testExecuteRequest() {
            Protocol.ExecuteRequest request = new Protocol.ExecuteRequest("SELECT 1");
            assertThat(request.sql).isEqualTo("SELECT 1");
            assertThat(request.fetchSize).isEqualTo(100);
        }

        @Test
        @DisplayName("ExecuteRequest should accept custom fetch size")
        void testExecuteRequestWithFetchSize() {
            Protocol.ExecuteRequest request = new Protocol.ExecuteRequest("SELECT 1", 500);
            assertThat(request.fetchSize).isEqualTo(500);
        }

        @Test
        @DisplayName("PrepareRequest should serialize SQL")
        void testPrepareRequest() {
            Protocol.PrepareRequest request = new Protocol.PrepareRequest("INSERT INTO t VALUES (?)");
            assertThat(request.sql).isEqualTo("INSERT INTO t VALUES (?)");
        }

        @Test
        @DisplayName("Parameter should hold index, type, and value")
        void testParameter() {
            Protocol.Parameter param = new Protocol.Parameter(1, "TEXT", "test value");
            assertThat(param.index).isEqualTo(1);
            assertThat(param.type).isEqualTo("TEXT");
            assertThat(param.value).isEqualTo("test value");
        }

        @Test
        @DisplayName("Parameter with null value should be valid")
        void testParameterWithNull() {
            Protocol.Parameter param = new Protocol.Parameter(1, "TEXT", null);
            assertThat(param.index).isEqualTo(1);
            assertThat(param.type).isEqualTo("TEXT");
            assertThat(param.value).isNull();
        }

        @Test
        @DisplayName("StatementExecuteRequest should set query flag")
        void testStatementExecuteRequest() {
            Protocol.StatementExecuteRequest request = new Protocol.StatementExecuteRequest(
                    java.util.List.of(new Protocol.Parameter(1, "TEXT", "test")),
                    true
            );
            assertThat(request.query).isTrue();
            assertThat(request.params).hasSize(1);
        }

        @Test
        @DisplayName("StatementBatchRequest should hold multiple parameter sets")
        void testStatementBatchRequest() {
            var paramSet1 = java.util.List.of(new Protocol.Parameter(1, "INTEGER", 1));
            var paramSet2 = java.util.List.of(new Protocol.Parameter(1, "INTEGER", 2));
            Protocol.StatementBatchRequest request = new Protocol.StatementBatchRequest(
                    java.util.List.of(paramSet1, paramSet2)
            );
            assertThat(request.paramSets).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Response Message Tests")
    class ResponseMessageTests {

        @Test
        @DisplayName("ExecuteResponse.isQuery should detect query results")
        void testExecuteResponseIsQuery() {
            Protocol.ExecuteResponse response = new Protocol.ExecuteResponse();
            response.columns = java.util.List.of(new Protocol.ColumnInfo());
            assertThat(response.isQuery()).isTrue();

            Protocol.ExecuteResponse updateResponse = new Protocol.ExecuteResponse();
            updateResponse.updateCount = 1;
            assertThat(updateResponse.isQuery()).isFalse();
        }

        @Test
        @DisplayName("ColumnInfo should hold column metadata")
        void testColumnInfo() {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = "id";
            col.type = "INTEGER";
            col.tableName = "test_table";
            col.autoIncrement = true;
            col.nullable = 1;

            assertThat(col.name).isEqualTo("id");
            assertThat(col.type).isEqualTo("INTEGER");
            assertThat(col.tableName).isEqualTo("test_table");
            assertThat(col.autoIncrement).isTrue();
            assertThat(col.nullable).isEqualTo(1);
        }

        @Test
        @DisplayName("ColumnInfo default nullable should be UNKNOWN")
        void testColumnInfoDefaultNullable() {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            assertThat(col.nullable).isEqualTo(2); // columnNullableUnknown
        }
    }
}
