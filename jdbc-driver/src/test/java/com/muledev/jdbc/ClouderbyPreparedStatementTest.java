package com.muledev.jdbc;

import com.muledev.jdbc.http.HttpClient;
import com.muledev.jdbc.http.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClouderbyPreparedStatement.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClouderbyPreparedStatement Tests")
class ClouderbyPreparedStatementTest {

    @Mock
    private HttpClient httpClient;

    private ClouderbyConnection connection;
    private ClouderbyPreparedStatement preparedStatement;

    @BeforeEach
    void setUp() throws Exception {
        // Create connection with mocked HttpClient
        connection = createMockedConnection(httpClient);

        // Setup default prepare response
        Protocol.PrepareResponse prepareResponse = new Protocol.PrepareResponse();
        prepareResponse.statementId = "stmt-1";
        prepareResponse.paramCount = 3;
        when(httpClient.prepareStatement(anyString())).thenReturn(prepareResponse);
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
    @DisplayName("Parameter Setting Tests")
    class ParameterSettingTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "SELECT * FROM t WHERE a=? AND b=? AND c=?");
        }

        @Test
        @DisplayName("setString should set TEXT parameter")
        void testSetString() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setString(1, "test value");
            preparedStatement.setString(2, "another");
            preparedStatement.setInt(3, 1);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params).hasSize(3);
            assertThat(params.get(0).type).isEqualTo("TEXT");
            assertThat(params.get(0).value).isEqualTo("test value");
            assertThat(params.get(1).type).isEqualTo("TEXT");
            assertThat(params.get(1).value).isEqualTo("another");
        }

        @Test
        @DisplayName("setInt should set INTEGER parameter")
        void testSetInt() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setInt(1, 42);
            preparedStatement.setInt(2, -100);
            preparedStatement.setInt(3, 0);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("INTEGER");
            assertThat(params.get(0).value).isEqualTo(42);
            assertThat(params.get(1).value).isEqualTo(-100);
            assertThat(params.get(2).value).isEqualTo(0);
        }

        @Test
        @DisplayName("setLong should set INTEGER parameter with long value")
        void testSetLong() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setLong(1, Long.MAX_VALUE);
            preparedStatement.setLong(2, Long.MIN_VALUE);
            preparedStatement.setLong(3, 0L);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("INTEGER");
            assertThat(params.get(0).value).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("setDouble should set REAL parameter")
        void testSetDouble() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setDouble(1, 3.14159);
            preparedStatement.setDouble(2, -0.001);
            preparedStatement.setDouble(3, 0.0);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("REAL");
            assertThat(params.get(0).value).isEqualTo(3.14159);
        }

        @Test
        @DisplayName("setBytes should set BLOB parameter with Base64 encoding")
        void testSetBytes() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            byte[] data = {0x01, 0x02, 0x03, (byte) 0xFF};
            preparedStatement.setBytes(1, data);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("BLOB");
            String base64 = (String) params.get(0).value;
            assertThat(Base64.getDecoder().decode(base64)).isEqualTo(data);
        }

        @Test
        @DisplayName("setDate should set TEXT parameter with date string")
        void testSetDate() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            Date date = Date.valueOf("2024-01-15");
            preparedStatement.setDate(1, date);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("TEXT");
            assertThat(params.get(0).value).isEqualTo("2024-01-15");
        }

        @Test
        @DisplayName("setTimestamp should set TEXT parameter with timestamp string")
        void testSetTimestamp() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            Timestamp ts = Timestamp.valueOf("2024-01-15 10:30:45.123");
            preparedStatement.setTimestamp(1, ts);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("TEXT");
            assertThat(params.get(0).value.toString()).contains("2024-01-15");
        }

        @Test
        @DisplayName("setBoolean should set INTEGER parameter (0 or 1)")
        void testSetBoolean() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setBoolean(1, true);
            preparedStatement.setBoolean(2, false);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("INTEGER");
            assertThat(params.get(0).value).isEqualTo(1);
            assertThat(params.get(1).value).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("NULL Handling Tests")
    class NullHandlingTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "INSERT INTO t VALUES (?, ?, ?)");
        }

        @Test
        @DisplayName("setNull should set TEXT type with null value")
        void testSetNull_Integer() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setNull(1, Types.INTEGER);
            preparedStatement.setNull(2, Types.VARCHAR);
            preparedStatement.setNull(3, Types.BLOB);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            // All NULL parameters should use TEXT type with null value
            assertThat(params.get(0).type).isEqualTo("TEXT");
            assertThat(params.get(0).value).isNull();
            assertThat(params.get(1).type).isEqualTo("TEXT");
            assertThat(params.get(1).value).isNull();
            assertThat(params.get(2).type).isEqualTo("TEXT");
            assertThat(params.get(2).value).isNull();
        }

        @Test
        @DisplayName("setString(null) should be equivalent to setNull")
        void testSetNull_String() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setString(1, null);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params.get(0).type).isEqualTo("TEXT");
            assertThat(params.get(0).value).isNull();
        }

        @Test
        @DisplayName("Unset parameter should become NULL")
        void testUnsetParameterBecomesNull() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            // Only set parameter 1, leave 2 and 3 unset
            preparedStatement.setInt(1, 42);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            List<Protocol.Parameter> params = paramsCaptor.getValue();
            assertThat(params).hasSize(3);
            assertThat(params.get(0).value).isEqualTo(42);
            // Unset parameters should be NULL
            assertThat(params.get(1).type).isEqualTo("NULL");
            assertThat(params.get(1).value).isNull();
            assertThat(params.get(2).type).isEqualTo("NULL");
            assertThat(params.get(2).value).isNull();
        }
    }

    @Nested
    @DisplayName("setObject Type Detection Tests")
    class SetObjectTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "INSERT INTO t VALUES (?, ?, ?)");
        }

        @Test
        @DisplayName("setObject with String should use TEXT type")
        void testSetObjectWithString() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, "test");
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("TEXT");
        }

        @Test
        @DisplayName("setObject with Integer should use INTEGER type")
        void testSetObjectWithInteger() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, 123);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("INTEGER");
            assertThat(paramsCaptor.getValue().get(0).value).isEqualTo(123);
        }

        @Test
        @DisplayName("setObject with Long should use INTEGER type")
        void testSetObjectWithLong() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, 123L);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("INTEGER");
        }

        @Test
        @DisplayName("setObject with Double should use REAL type")
        void testSetObjectWithDouble() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, 3.14);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("REAL");
        }

        @Test
        @DisplayName("setObject with byte[] should use BLOB type")
        void testSetObjectWithByteArray() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, new byte[]{1, 2, 3});
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("BLOB");
        }

        @Test
        @DisplayName("setObject with Boolean should use INTEGER type")
        void testSetObjectWithBoolean() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, true);
            preparedStatement.setObject(2, false);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("INTEGER");
            assertThat(paramsCaptor.getValue().get(0).value).isEqualTo(1);
            assertThat(paramsCaptor.getValue().get(1).value).isEqualTo(0);
        }

        @Test
        @DisplayName("setObject with BigDecimal should use TEXT type")
        void testSetObjectWithBigDecimal() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, new BigDecimal("123.456"));
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("TEXT");
            assertThat(paramsCaptor.getValue().get(0).value).isEqualTo("123.456");
        }

        @Test
        @DisplayName("setObject with null should set NULL")
        void testSetObjectWithNull() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, null);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).value).isNull();
        }

        @Test
        @DisplayName("setObject with unknown type should use toString")
        void testSetObjectWithUnknownType() throws SQLException {
            Protocol.UpdateResponse response = new Protocol.UpdateResponse();
            response.updateCount = 1;
            when(httpClient.executePreparedUpdate(anyString(), anyList())).thenReturn(response);

            preparedStatement.setObject(1, new java.util.Date(0));
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.executeUpdate();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedUpdate(eq("stmt-1"), paramsCaptor.capture());

            assertThat(paramsCaptor.getValue().get(0).type).isEqualTo("TEXT");
        }
    }

    @Nested
    @DisplayName("Batch Tests")
    class BatchTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "INSERT INTO t VALUES (?, ?, ?)");
        }

        @Test
        @DisplayName("addBatch should add current parameters to batch")
        void testAddBatch() throws SQLException {
            Protocol.StatementBatchResponse response = new Protocol.StatementBatchResponse();
            response.updateCounts = List.of(1, 1);
            when(httpClient.executePreparedBatch(anyString(), anyList())).thenReturn(response);

            preparedStatement.setInt(1, 1);
            preparedStatement.setString(2, "first");
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.addBatch();

            preparedStatement.setInt(1, 2);
            preparedStatement.setString(2, "second");
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.addBatch();

            int[] results = preparedStatement.executeBatch();

            // Verify batch was executed (results come from mock)
            assertThat(results).hasSize(2);
            verify(httpClient).executePreparedBatch(eq("stmt-1"), anyList());
        }

        @Test
        @DisplayName("clearBatch should remove all batched parameters")
        void testClearBatch() throws SQLException {
            preparedStatement.setInt(1, 1);
            preparedStatement.setString(2, "test");
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.addBatch();

            preparedStatement.clearBatch();

            int[] results = preparedStatement.executeBatch();
            assertThat(results).isEmpty();

            verify(httpClient, never()).executePreparedBatch(anyString(), anyList());
        }

        @Test
        @DisplayName("executeBatch with empty batch should return empty array")
        void testExecuteEmptyBatch() throws SQLException {
            int[] results = preparedStatement.executeBatch();
            assertThat(results).isEmpty();
            verify(httpClient, never()).executePreparedBatch(anyString(), anyList());
        }

        @Test
        @DisplayName("executeBatch should clear batch after execution")
        void testExecuteBatchClearsBatch() throws SQLException {
            Protocol.StatementBatchResponse response = new Protocol.StatementBatchResponse();
            response.updateCounts = List.of(1);
            when(httpClient.executePreparedBatch(anyString(), anyList())).thenReturn(response);

            preparedStatement.setInt(1, 1);
            preparedStatement.setString(2, "test");
            preparedStatement.setNull(3, Types.INTEGER);
            preparedStatement.addBatch();
            preparedStatement.executeBatch();

            // Second execute should be empty
            int[] results = preparedStatement.executeBatch();
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "SELECT * FROM t WHERE a=? AND b=? AND c=?");
        }

        @Test
        @DisplayName("clearParameters should clear all set parameters")
        void testClearParameters() throws SQLException {
            Protocol.QueryResponse response = new Protocol.QueryResponse();
            response.columns = List.of();
            response.rows = List.of();
            response.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(response);

            preparedStatement.setInt(1, 42);
            preparedStatement.setString(2, "test");
            preparedStatement.setDouble(3, 3.14);

            preparedStatement.clearParameters();

            preparedStatement.executeQuery();

            ArgumentCaptor<List<Protocol.Parameter>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            verify(httpClient).executePreparedQuery(eq("stmt-1"), paramsCaptor.capture(), anyInt());

            // All parameters should be NULL after clear
            List<Protocol.Parameter> params = paramsCaptor.getValue();
            for (Protocol.Parameter param : params) {
                assertThat(param.type).isEqualTo("NULL");
                assertThat(param.value).isNull();
            }
        }

        @Test
        @DisplayName("Stream parameters should throw SQLFeatureNotSupportedException")
        void testStreamParametersThrowUnsupported() throws SQLException {
            assertThatThrownBy(() -> preparedStatement.setAsciiStream(1, null, 0))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(() -> preparedStatement.setUnicodeStream(1, null, 0))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(() -> preparedStatement.setBinaryStream(1, null, 0))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
            assertThatThrownBy(() -> preparedStatement.setCharacterStream(1, null, 0))
                    .isInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        @DisplayName("execute should detect query vs update based on SQL")
        void testExecuteDetectsQueryVsUpdate() throws SQLException {
            // Test with SELECT
            Protocol.QueryResponse queryResponse = new Protocol.QueryResponse();
            queryResponse.columns = List.of();
            queryResponse.rows = List.of();
            queryResponse.done = true;
            when(httpClient.executePreparedQuery(anyString(), anyList(), anyInt())).thenReturn(queryResponse);

            preparedStatement.setNull(1, Types.INTEGER);
            preparedStatement.setNull(2, Types.INTEGER);
            preparedStatement.setNull(3, Types.INTEGER);
            boolean isQuery = preparedStatement.execute();

            assertThat(isQuery).isTrue();
            verify(httpClient).executePreparedQuery(anyString(), anyList(), anyInt());
        }

        @Test
        @DisplayName("getParameterMetaData should return metadata with param count")
        void testGetParameterMetaData() throws SQLException {
            ParameterMetaData metaData = preparedStatement.getParameterMetaData();
            assertThat(metaData.getParameterCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @BeforeEach
        void createStatement() throws SQLException {
            preparedStatement = new ClouderbyPreparedStatement(connection, "SELECT 1");
        }

        @Test
        @DisplayName("close should close statement on server")
        void testClose() throws SQLException {
            preparedStatement.close();
            verify(httpClient).closeStatement("stmt-1");
        }

        @Test
        @DisplayName("Operations on closed statement should throw SQLException")
        void testOperationsOnClosedStatement() throws SQLException {
            preparedStatement.close();

            assertThatThrownBy(() -> preparedStatement.executeQuery())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> preparedStatement.executeUpdate())
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");

            assertThatThrownBy(() -> preparedStatement.setString(1, "test"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("isClosed should return true after close")
        void testIsClosed() throws SQLException {
            assertThat(preparedStatement.isClosed()).isFalse();
            preparedStatement.close();
            assertThat(preparedStatement.isClosed()).isTrue();
        }
    }
}
