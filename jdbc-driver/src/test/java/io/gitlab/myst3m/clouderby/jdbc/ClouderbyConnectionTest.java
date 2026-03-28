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

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClouderbyConnection.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClouderbyConnection Tests")
class ClouderbyConnectionTest {

    @Mock
    private HttpClient httpClient;

    private ClouderbyConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = createMockedConnection(httpClient);
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

        java.lang.reflect.Field autoCommitField = ClouderbyConnection.class.getDeclaredField("autoCommit");
        autoCommitField.setAccessible(true);
        autoCommitField.set(conn, true);

        java.lang.reflect.Field transactionIsolationField = ClouderbyConnection.class.getDeclaredField("transactionIsolation");
        transactionIsolationField.setAccessible(true);
        transactionIsolationField.set(conn, Connection.TRANSACTION_SERIALIZABLE);

        return conn;
    }

    private sun.misc.Unsafe getUnsafe() throws Exception {
        java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    @Nested
    @DisplayName("Statement Creation Tests")
    class StatementCreationTests {

        @Test
        @DisplayName("createStatement should return ClouderbyStatement")
        void testCreateStatement() throws SQLException {
            Statement stmt = connection.createStatement();
            assertThat(stmt).isInstanceOf(ClouderbyStatement.class);
        }

        @Test
        @DisplayName("prepareStatement should return ClouderbyPreparedStatement")
        void testPrepareStatement() throws SQLException {
            Protocol.PrepareResponse response = new Protocol.PrepareResponse();
            response.statementId = "stmt-1";
            response.paramCount = 1;
            when(httpClient.prepareStatement(anyString())).thenReturn(response);

            PreparedStatement ps = connection.prepareStatement("SELECT ?");
            assertThat(ps).isInstanceOf(ClouderbyPreparedStatement.class);
        }

        @Test
        @DisplayName("prepareCall should throw SQLFeatureNotSupportedException")
        void testPrepareCallNotSupported() {
            assertThatThrownBy(() -> connection.prepareCall("{call procedure()}"))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Nested
    @DisplayName("Auto-Commit Tests")
    class AutoCommitTests {

        @Test
        @DisplayName("Auto-commit should be true by default")
        void testAutoCommitDefault() throws SQLException {
            assertThat(connection.getAutoCommit()).isTrue();
        }

        @Test
        @DisplayName("Setting auto-commit false should begin transaction")
        void testSetAutoCommitFalse() throws SQLException {
            Protocol.TransactionResponse response = new Protocol.TransactionResponse();
            response.status = "ok";
            when(httpClient.beginTransaction()).thenReturn(response);

            connection.setAutoCommit(false);

            assertThat(connection.getAutoCommit()).isFalse();
            verify(httpClient).beginTransaction();
        }

        @Test
        @DisplayName("Setting auto-commit true should commit transaction")
        void testSetAutoCommitTrue() throws SQLException {
            // First set to false to start transaction
            Protocol.TransactionResponse beginResponse = new Protocol.TransactionResponse();
            beginResponse.status = "ok";
            when(httpClient.beginTransaction()).thenReturn(beginResponse);
            connection.setAutoCommit(false);

            // Then set back to true
            Protocol.TransactionResponse commitResponse = new Protocol.TransactionResponse();
            commitResponse.status = "ok";
            when(httpClient.commitTransaction()).thenReturn(commitResponse);
            connection.setAutoCommit(true);

            assertThat(connection.getAutoCommit()).isTrue();
            verify(httpClient).commitTransaction();
        }

        @Test
        @DisplayName("Setting same auto-commit value should be no-op")
        void testSetAutoCommitSameValue() throws SQLException {
            connection.setAutoCommit(true);
            verify(httpClient, never()).beginTransaction();
            verify(httpClient, never()).commitTransaction();
        }
    }

    @Nested
    @DisplayName("Transaction Tests")
    class TransactionTests {

        @BeforeEach
        void startTransaction() throws SQLException {
            Protocol.TransactionResponse response = new Protocol.TransactionResponse();
            response.status = "ok";
            when(httpClient.beginTransaction()).thenReturn(response);
            connection.setAutoCommit(false);
        }

        @Test
        @DisplayName("commit should commit transaction")
        void testCommit() throws SQLException {
            Protocol.TransactionResponse commitResponse = new Protocol.TransactionResponse();
            commitResponse.status = "ok";
            when(httpClient.commitTransaction()).thenReturn(commitResponse);

            connection.commit();

            verify(httpClient, times(1)).commitTransaction();
            // Should start a new transaction
            verify(httpClient, times(2)).beginTransaction();
        }

        @Test
        @DisplayName("rollback should rollback transaction")
        void testRollback() throws SQLException {
            Protocol.TransactionResponse rollbackResponse = new Protocol.TransactionResponse();
            rollbackResponse.status = "ok";
            when(httpClient.rollbackTransaction()).thenReturn(rollbackResponse);

            connection.rollback();

            verify(httpClient).rollbackTransaction();
            // Should start a new transaction
            verify(httpClient, times(2)).beginTransaction();
        }

        @Test
        @DisplayName("commit in auto-commit mode should throw SQLException")
        void testCommitInAutoCommitMode() throws SQLException {
            Protocol.TransactionResponse commitResponse = new Protocol.TransactionResponse();
            when(httpClient.commitTransaction()).thenReturn(commitResponse);
            connection.setAutoCommit(true);

            assertThatThrownBy(() -> connection.commit())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("autoCommit");
        }

        @Test
        @DisplayName("rollback in auto-commit mode should throw SQLException")
        void testRollbackInAutoCommitMode() throws SQLException {
            Protocol.TransactionResponse commitResponse = new Protocol.TransactionResponse();
            when(httpClient.commitTransaction()).thenReturn(commitResponse);
            connection.setAutoCommit(true);

            assertThatThrownBy(() -> connection.rollback())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("autoCommit");
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("close should close connection on server")
        void testClose() throws SQLException {
            Protocol.CloseResponse response = new Protocol.CloseResponse();
            response.closed = true;
            when(httpClient.closeConnection()).thenReturn(response);

            connection.close();

            verify(httpClient).closeConnection();
            assertThat(connection.isClosed()).isTrue();
        }

        @Test
        @DisplayName("close with pending transaction should rollback")
        void testCloseWithPendingTransaction() throws SQLException {
            Protocol.TransactionResponse txResponse = new Protocol.TransactionResponse();
            txResponse.status = "ok";
            when(httpClient.beginTransaction()).thenReturn(txResponse);
            when(httpClient.rollbackTransaction()).thenReturn(txResponse);

            connection.setAutoCommit(false);

            Protocol.CloseResponse closeResponse = new Protocol.CloseResponse();
            closeResponse.closed = true;
            when(httpClient.closeConnection()).thenReturn(closeResponse);

            connection.close();

            verify(httpClient).rollbackTransaction();
        }

        @Test
        @DisplayName("Operations on closed connection should throw SQLException")
        void testOperationsOnClosedConnection() throws SQLException {
            Protocol.CloseResponse response = new Protocol.CloseResponse();
            response.closed = true;
            when(httpClient.closeConnection()).thenReturn(response);
            connection.close();

            assertThatThrownBy(() -> connection.createStatement())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> connection.prepareStatement("SELECT 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("isValid Tests")
    class IsValidTests {

        @Test
        @DisplayName("isValid should return true when health check succeeds")
        void testIsValid() throws SQLException {
            Protocol.HealthResponse response = new Protocol.HealthResponse();
            response.status = "ok";
            when(httpClient.health()).thenReturn(response);

            assertThat(connection.isValid(5)).isTrue();
        }

        @Test
        @DisplayName("isValid should return false when health check fails")
        void testIsValidFails() throws SQLException {
            when(httpClient.health()).thenThrow(new SQLException("Connection failed"));

            assertThat(connection.isValid(5)).isFalse();
        }

        @Test
        @DisplayName("isValid should return false when connection is closed")
        void testIsValidWhenClosed() throws SQLException {
            Protocol.CloseResponse response = new Protocol.CloseResponse();
            response.closed = true;
            when(httpClient.closeConnection()).thenReturn(response);
            connection.close();

            assertThat(connection.isValid(5)).isFalse();
        }
    }

    @Nested
    @DisplayName("Metadata Tests")
    class MetadataTests {

        @Test
        @DisplayName("getCatalog should return database name")
        void testGetCatalog() throws SQLException {
            assertThat(connection.getCatalog()).isEqualTo("test");
        }

        @Test
        @DisplayName("getSchema should return null")
        void testGetSchema() throws SQLException {
            assertThat(connection.getSchema()).isNull();
        }

        @Test
        @DisplayName("getMetaData should return ClouderbyDatabaseMetaData")
        void testGetMetaData() throws SQLException {
            assertThat(connection.getMetaData()).isInstanceOf(ClouderbyDatabaseMetaData.class);
        }

        @Test
        @DisplayName("getTransactionIsolation should return SERIALIZABLE")
        void testGetTransactionIsolation() throws SQLException {
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
        }

        @Test
        @DisplayName("setTransactionIsolation should update isolation level")
        void testSetTransactionIsolation() throws SQLException {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }

        @Test
        @DisplayName("getHoldability should return HOLD_CURSORS_OVER_COMMIT")
        void testGetHoldability() throws SQLException {
            assertThat(connection.getHoldability()).isEqualTo(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        }
    }

    @Nested
    @DisplayName("Unsupported Features Tests")
    class UnsupportedFeaturesTests {

        @Test
        @DisplayName("setSavepoint should throw SQLFeatureNotSupportedException")
        void testSavepointNotSupported() {
            assertThatThrownBy(() -> connection.setSavepoint())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(() -> connection.setSavepoint("sp1"))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("createClob should throw SQLFeatureNotSupportedException")
        void testCreateClobNotSupported() {
            assertThatThrownBy(() -> connection.createClob())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("createBlob should throw SQLFeatureNotSupportedException")
        void testCreateBlobNotSupported() {
            assertThatThrownBy(() -> connection.createBlob())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("getTypeMap should throw SQLFeatureNotSupportedException")
        void testGetTypeMapNotSupported() {
            assertThatThrownBy(() -> connection.getTypeMap())
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }
    }

    @Nested
    @DisplayName("Wrapper Tests")
    class WrapperTests {

        @Test
        @DisplayName("isWrapperFor should return true for ClouderbyConnection")
        void testIsWrapperFor() throws SQLException {
            assertThat(connection.isWrapperFor(ClouderbyConnection.class)).isTrue();
            assertThat(connection.isWrapperFor(Connection.class)).isTrue();
        }

        @Test
        @DisplayName("unwrap should return this for ClouderbyConnection")
        void testUnwrap() throws SQLException {
            assertThat(connection.unwrap(ClouderbyConnection.class)).isSameAs(connection);
        }

        @Test
        @DisplayName("unwrap should throw for incompatible class")
        void testUnwrapIncompatible() {
            assertThatThrownBy(() -> connection.unwrap(String.class))
                    .isInstanceOf(SQLException.class);
        }
    }
}
