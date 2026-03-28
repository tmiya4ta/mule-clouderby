package io.gitlab.myst3m.clouderby.jdbc;

import io.gitlab.myst3m.clouderby.jdbc.http.HttpClient;
import io.gitlab.myst3m.clouderby.jdbc.http.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClouderbyStatement.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClouderbyStatement Tests")
class ClouderbyStatementTest {

    @Mock
    private HttpClient httpClient;

    private ClouderbyConnection connection;
    private ClouderbyStatement statement;

    @BeforeEach
    void setUp() throws Exception {
        connection = createMockedConnection(httpClient);
        statement = new ClouderbyStatement(connection);
    }

    private ClouderbyConnection createMockedConnection(HttpClient client) throws Exception {
        sun.misc.Unsafe unsafe = getUnsafe();
        ClouderbyConnection conn = (ClouderbyConnection) unsafe.allocateInstance(ClouderbyConnection.class);

        java.lang.reflect.Field httpClientField = ClouderbyConnection.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(conn, client);

        java.lang.reflect.Field databaseField = ClouderbyConnection.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        databaseField.set(conn, "test");

        return conn;
    }

    private sun.misc.Unsafe getUnsafe() throws Exception {
        java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    @Nested
    @DisplayName("Query Execution Tests")
    class QueryExecutionTests {

        @Test
        @DisplayName("executeQuery should return ResultSet")
        void testExecuteQuery() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executeQuery(anyString(), anyInt())).thenReturn(response);

            ResultSet rs = statement.executeQuery("SELECT 1");

            assertThat(rs).isNotNull();
            assertThat(rs).isInstanceOf(ClouderbyResultSet.class);
            verify(httpClient).executeQuery("SELECT 1", 100);
        }

        @Test
        @DisplayName("executeUpdate should return update count")
        void testExecuteUpdate() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 5;
            response.lastInsertId = 10L;
            when(httpClient.executeUpdate(anyString())).thenReturn(response);

            int count = statement.executeUpdate("INSERT INTO t VALUES (1)");

            assertThat(count).isEqualTo(5);
            verify(httpClient).executeUpdate("INSERT INTO t VALUES (1)");
        }

        @Test
        @DisplayName("execute with SELECT should return true")
        void testExecute_Query() throws SQLException {
            Protocol.ExecuteResponse response = new Protocol.ExecuteResponse();
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = "test";
            col.type = "INTEGER";
            response.columns = List.of(col);
            response.rows = List.of();
            response.done = true;
            when(httpClient.execute(anyString(), anyInt())).thenReturn(response);

            boolean isQuery = statement.execute("SELECT 1");

            assertThat(isQuery).isTrue();
            assertThat(statement.getResultSet()).isNotNull();
            assertThat(statement.getUpdateCount()).isEqualTo(-1);
        }

        @Test
        @DisplayName("execute with INSERT should return false")
        void testExecute_Update() throws SQLException {
            Protocol.ExecuteResponse response = new Protocol.ExecuteResponse();
            response.updateCount = 1;
            response.lastInsertId = 1L;
            when(httpClient.execute(anyString(), anyInt())).thenReturn(response);

            boolean isQuery = statement.execute("INSERT INTO t VALUES (1)");

            assertThat(isQuery).isFalse();
            assertThat(statement.getResultSet()).isNull();
            assertThat(statement.getUpdateCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ResultSet Access Tests")
    class ResultSetAccessTests {

        @Test
        @DisplayName("getResultSet should return current ResultSet")
        void testGetResultSet() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executeQuery(anyString(), anyInt())).thenReturn(response);

            ResultSet rs1 = statement.executeQuery("SELECT 1");
            ResultSet rs2 = statement.getResultSet();

            assertThat(rs1).isSameAs(rs2);
        }

        @Test
        @DisplayName("getMoreResults should close current ResultSet")
        void testGetMoreResults() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executeQuery(anyString(), anyInt())).thenReturn(response);

            statement.executeQuery("SELECT 1");
            boolean hasMore = statement.getMoreResults();

            assertThat(hasMore).isFalse();
            assertThat(statement.getResultSet()).isNull();
        }

        @Test
        @DisplayName("getGeneratedKeys should return last insert ID")
        void testGetGeneratedKeys() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            response.lastInsertId = 42L;
            when(httpClient.executeUpdate(anyString())).thenReturn(response);

            statement.executeUpdate("INSERT INTO t VALUES (DEFAULT)");
            ResultSet keys = statement.getGeneratedKeys();

            assertThat(keys.next()).isTrue();
            assertThat(keys.getLong(1)).isEqualTo(42L);
        }

        @Test
        @DisplayName("getGeneratedKeys should return empty ResultSet when no insert")
        void testGetGeneratedKeysEmpty() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 0;
            response.lastInsertId = null;
            when(httpClient.executeUpdate(anyString())).thenReturn(response);

            statement.executeUpdate("UPDATE t SET x = 1");
            ResultSet keys = statement.getGeneratedKeys();

            assertThat(keys.next()).isFalse();
        }
    }

    @Nested
    @DisplayName("Batch Execution Tests")
    class BatchExecutionTests {

        @Test
        @DisplayName("addBatch should add SQL to batch")
        void testAddBatch() throws SQLException {
            statement.addBatch("INSERT INTO t VALUES (1)");
            statement.addBatch("INSERT INTO t VALUES (2)");

            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executeUpdate(anyString())).thenReturn(response);

            int[] results = statement.executeBatch();

            assertThat(results).hasSize(2);
            verify(httpClient, times(2)).executeUpdate(anyString());
        }

        @Test
        @DisplayName("clearBatch should remove all batched SQL")
        void testClearBatch() throws SQLException {
            statement.addBatch("INSERT INTO t VALUES (1)");
            statement.clearBatch();

            int[] results = statement.executeBatch();

            assertThat(results).isEmpty();
            verify(httpClient, never()).executeUpdate(anyString());
        }

        @Test
        @DisplayName("executeBatch should clear batch after execution")
        void testExecuteBatchClearsBatch() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executeUpdate(anyString())).thenReturn(response);

            statement.addBatch("INSERT INTO t VALUES (1)");
            statement.executeBatch();

            // Second execute should return empty
            int[] results = statement.executeBatch();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("getFetchSize should return default 100")
        void testGetFetchSize() throws SQLException {
            assertThat(statement.getFetchSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("setFetchSize should update fetch size")
        void testSetFetchSize() throws SQLException {
            statement.setFetchSize(500);
            assertThat(statement.getFetchSize()).isEqualTo(500);
        }

        @Test
        @DisplayName("getMaxRows should return default 0")
        void testGetMaxRows() throws SQLException {
            assertThat(statement.getMaxRows()).isEqualTo(0);
        }

        @Test
        @DisplayName("setMaxRows should update max rows")
        void testSetMaxRows() throws SQLException {
            statement.setMaxRows(1000);
            assertThat(statement.getMaxRows()).isEqualTo(1000);
        }

        @Test
        @DisplayName("getQueryTimeout should return default 0")
        void testGetQueryTimeout() throws SQLException {
            assertThat(statement.getQueryTimeout()).isEqualTo(0);
        }

        @Test
        @DisplayName("setQueryTimeout should update timeout")
        void testSetQueryTimeout() throws SQLException {
            statement.setQueryTimeout(30);
            assertThat(statement.getQueryTimeout()).isEqualTo(30);
        }

        @Test
        @DisplayName("getResultSetType should return TYPE_FORWARD_ONLY")
        void testGetResultSetType() throws SQLException {
            assertThat(statement.getResultSetType()).isEqualTo(ResultSet.TYPE_FORWARD_ONLY);
        }

        @Test
        @DisplayName("getResultSetConcurrency should return CONCUR_READ_ONLY")
        void testGetResultSetConcurrency() throws SQLException {
            assertThat(statement.getResultSetConcurrency()).isEqualTo(ResultSet.CONCUR_READ_ONLY);
        }

        @Test
        @DisplayName("getFetchDirection should return FETCH_FORWARD")
        void testGetFetchDirection() throws SQLException {
            assertThat(statement.getFetchDirection()).isEqualTo(ResultSet.FETCH_FORWARD);
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("close should mark statement as closed")
        void testClose() throws SQLException {
            assertThat(statement.isClosed()).isFalse();
            statement.close();
            assertThat(statement.isClosed()).isTrue();
        }

        @Test
        @DisplayName("close should close current ResultSet")
        void testCloseClosesResultSet() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executeQuery(anyString(), anyInt())).thenReturn(response);

            ResultSet rs = statement.executeQuery("SELECT 1");
            statement.close();

            assertThat(rs.isClosed()).isTrue();
        }

        @Test
        @DisplayName("Operations on closed statement should throw SQLException")
        void testOperationsOnClosedStatement() throws SQLException {
            statement.close();

            assertThatThrownBy(() -> statement.executeQuery("SELECT 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> statement.executeUpdate("UPDATE t SET x = 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> statement.execute("SELECT 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("Connection Access Tests")
    class ConnectionAccessTests {

        @Test
        @DisplayName("getConnection should return owning connection")
        void testGetConnection() throws SQLException {
            assertThat(statement.getConnection()).isSameAs(connection);
        }
    }

    @Nested
    @DisplayName("Wrapper Tests")
    class WrapperTests {

        @Test
        @DisplayName("isWrapperFor should return true for ClouderbyStatement")
        void testIsWrapperFor() throws SQLException {
            assertThat(statement.isWrapperFor(ClouderbyStatement.class)).isTrue();
            assertThat(statement.isWrapperFor(Statement.class)).isTrue();
        }

        @Test
        @DisplayName("unwrap should return this for ClouderbyStatement")
        void testUnwrap() throws SQLException {
            assertThat(statement.unwrap(ClouderbyStatement.class)).isSameAs(statement);
        }

        @Test
        @DisplayName("unwrap should throw for incompatible class")
        void testUnwrapIncompatible() {
            assertThatThrownBy(() -> statement.unwrap(String.class))
                    .isInstanceOf(SQLException.class);
        }
    }
}
