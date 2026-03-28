package io.gitlab.myst3m.clouderby.jdbc;

import io.gitlab.myst3m.clouderby.jdbc.http.HttpClient;
import io.gitlab.myst3m.clouderby.jdbc.http.Protocol;

import java.sql.*;

/**
 * JDBC Statement implementation for clouderbyd.
 */
public class ClouderbyStatement implements Statement {
    protected final ClouderbyConnection connection;
    protected final HttpClient httpClient;
    protected boolean closed = false;
    protected int fetchSize = 100;
    protected int maxRows = 0;
    protected int queryTimeout = 0;
    protected ResultSet currentResultSet;
    protected int updateCount = -1;
    protected Long lastInsertId;
    protected java.util.List<String> batchSqls = new java.util.ArrayList<>();

    public ClouderbyStatement(ClouderbyConnection connection) {
        this.connection = connection;
        this.httpClient = connection.getHttpClient();
    }

    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        Protocol.QueryResponse response = httpClient.executeQuery(sql, fetchSize);
        currentResultSet = new ClouderbyResultSet(this, response);
        updateCount = -1;
        return currentResultSet;
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        Protocol.UpdateResponse response = httpClient.executeUpdate(sql);
        updateCount = response.updateCount;
        lastInsertId = response.lastInsertId;
        currentResultSet = null;
        return updateCount;
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            if (currentResultSet != null) {
                currentResultSet.close();
            }
            closed = true;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        checkClosed();
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        checkClosed();
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkClosed();
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        checkClosed();
        this.maxRows = max;
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        checkClosed();
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        return queryTimeout;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        this.queryTimeout = seconds;
    }

    @Override
    public void cancel() throws SQLException {
        checkClosed();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        checkClosed();
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        Protocol.ExecuteResponse response = httpClient.execute(sql, fetchSize);

        if (response.isQuery()) {
            // Convert to QueryResponse for ResultSet
            Protocol.QueryResponse queryResponse = new Protocol.QueryResponse();
            queryResponse.columns = response.columns;
            queryResponse.rows = response.rows;
            queryResponse.done = response.done != null ? response.done : true;
            queryResponse.cursorId = response.cursorId;
            currentResultSet = new ClouderbyResultSet(this, queryResponse);
            updateCount = -1;
            return true;
        } else {
            currentResultSet = null;
            updateCount = response.updateCount != null ? response.updateCount : 0;
            lastInsertId = response.lastInsertId;
            return false;
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        return updateCount;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        updateCount = -1;
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        checkClosed();
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        this.fetchSize = rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return fetchSize;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkClosed();
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        checkClosed();
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        checkClosed();
        batchSqls.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        batchSqls.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        if (batchSqls.isEmpty()) {
            return new int[0];
        }
        try {
            int[] results = new int[batchSqls.size()];
            for (int i = 0; i < batchSqls.size(); i++) {
                Protocol.UpdateResponse response = httpClient.executeUpdate(batchSqls.get(i));
                results[i] = response.updateCount;
            }
            return results;
        } finally {
            batchSqls.clear();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkClosed();
        return connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkClosed();
        if (lastInsertId == null) {
            return new ClouderbyResultSet(this, new Protocol.QueryResponse());
        }
        Protocol.QueryResponse response = new Protocol.QueryResponse();
        response.columns = java.util.List.of(new Protocol.ColumnInfo());
        response.columns.get(0).name = "GENERATED_KEY";
        response.columns.get(0).type = "INTEGER";
        response.rows = java.util.List.of(java.util.List.of(lastInsertId));
        response.done = true;
        return new ClouderbyResultSet(this, response);
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        checkClosed();
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        checkClosed();
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        checkClosed();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }
}
