package com.muledev.jdbc.support;

import com.muledev.jdbc.ClouderbyConnection;
import com.muledev.jdbc.http.HttpClient;
import com.muledev.jdbc.http.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Base class for unit tests that mock the HttpClient.
 */
@ExtendWith(MockitoExtension.class)
public abstract class MockedHttpClientTestBase {

    @Mock
    protected HttpClient httpClient;

    protected ClouderbyConnection connection;

    /**
     * Creates a connection with a mocked HttpClient.
     * Subclasses should call this after setting up their mock expectations.
     */
    protected void initConnection() throws SQLException {
        // Create a mock open response
        Protocol.OpenResponse openResponse = new Protocol.OpenResponse();
        openResponse.sessionId = "test-session-id";
        openResponse.serverVersion = "1.0.0";

        when(httpClient.openConnection(anyString())).thenReturn(openResponse);

        // Create connection and inject mocked httpClient
        connection = createConnectionWithMockedClient();
    }

    /**
     * Creates an ClouderbyConnection with the mocked HttpClient injected.
     */
    private ClouderbyConnection createConnectionWithMockedClient() throws SQLException {
        try {
            // Use reflection to create connection without calling constructor
            ClouderbyConnection conn = createConnectionInstance();

            // Inject mocked HttpClient
            Field httpClientField = ClouderbyConnection.class.getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(conn, httpClient);

            // Inject database name
            Field databaseField = ClouderbyConnection.class.getDeclaredField("database");
            databaseField.setAccessible(true);
            databaseField.set(conn, "test-db");

            return conn;
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to create mocked connection", e);
        }
    }

    /**
     * Creates an ClouderbyConnection instance without invoking the constructor.
     */
    @SuppressWarnings("unchecked")
    private ClouderbyConnection createConnectionInstance() throws ReflectiveOperationException {
        // Use Unsafe to allocate instance without calling constructor
        sun.misc.Unsafe unsafe = getUnsafe();
        return (ClouderbyConnection) unsafe.allocateInstance(ClouderbyConnection.class);
    }

    private sun.misc.Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    /**
     * Creates a Protocol.QueryResponse for testing.
     */
    protected Protocol.QueryResponse createQueryResponse(String[] columnNames, String[] columnTypes, Object[][] rows) {
        Protocol.QueryResponse response = new Protocol.QueryResponse();
        response.columns = new java.util.ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            Protocol.ColumnInfo col = new Protocol.ColumnInfo();
            col.name = columnNames[i];
            col.type = columnTypes[i];
            response.columns.add(col);
        }
        response.rows = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            response.rows.add(java.util.Arrays.asList(row));
        }
        response.done = true;
        return response;
    }

    /**
     * Creates a Protocol.UpdateResponse for testing.
     */
    protected Protocol.UpdateResponse createUpdateResponse(int updateCount, Long lastInsertId) {
        Protocol.UpdateResponse response = new Protocol.UpdateResponse();
        response.updateCount = updateCount;
        response.lastInsertId = lastInsertId;
        return response;
    }

    /**
     * Creates a Protocol.PrepareResponse for testing.
     */
    protected Protocol.PrepareResponse createPrepareResponse(String statementId, int paramCount) {
        Protocol.PrepareResponse response = new Protocol.PrepareResponse();
        response.statementId = statementId;
        response.paramCount = paramCount;
        return response;
    }
}
