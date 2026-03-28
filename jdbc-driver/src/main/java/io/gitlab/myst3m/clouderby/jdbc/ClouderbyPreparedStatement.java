package io.gitlab.myst3m.clouderby.jdbc;

import io.gitlab.myst3m.clouderby.jdbc.http.HttpClient;
import io.gitlab.myst3m.clouderby.jdbc.http.Protocol;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC PreparedStatement implementation for clouderbyd.
 */
public class ClouderbyPreparedStatement extends ClouderbyStatement implements PreparedStatement {
    private final String sql;
    private final String statementId;
    private final int paramCount;
    private final Map<Integer, Protocol.Parameter> parameters = new HashMap<>();
    private final List<List<Protocol.Parameter>> batchParamSets = new ArrayList<>();

    public ClouderbyPreparedStatement(ClouderbyConnection connection, String sql) throws SQLException {
        super(connection);
        this.sql = sql;
        Protocol.PrepareResponse response = httpClient.prepareStatement(sql);
        this.statementId = response.statementId;
        this.paramCount = response.paramCount;
    }

    private boolean isQuery() {
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("SELECT") || trimmed.startsWith("WITH");
    }

    private List<Protocol.Parameter> getParameterList() {
        List<Protocol.Parameter> params = new ArrayList<>();
        for (int i = 1; i <= paramCount; i++) {
            Protocol.Parameter p = parameters.get(i);
            if (p == null) {
                p = new Protocol.Parameter(i, "NULL", null);
            }
            params.add(p);
        }
        return params;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkClosed();
        List<Protocol.Parameter> params = getParameterList();
        Protocol.QueryResponse response = httpClient.executePreparedQuery(statementId, params, fetchSize);
        currentResultSet = new ClouderbyResultSet(this, response);
        updateCount = -1;
        return currentResultSet;
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkClosed();
        Protocol.UpdateResponse response = httpClient.executePreparedUpdate(statementId, getParameterList());
        updateCount = response.updateCount;
        lastInsertId = response.lastInsertId;
        currentResultSet = null;
        return updateCount;
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        checkClosed();
        // Use TEXT type with null value instead of NULL type to pass API validation
        // Server treats null value as SQL NULL regardless of type
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", null));
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "INTEGER", x ? 1 : 0));
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "INTEGER", (int) x));
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "INTEGER", (int) x));
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "INTEGER", x));
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "INTEGER", x));
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "REAL", (double) x));
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        checkClosed();
        parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "REAL", x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.DECIMAL);
        } else {
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", x.toString()));
        }
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.VARCHAR);
        } else {
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", x));
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.BLOB);
        } else {
            String base64 = Base64.getEncoder().encodeToString(x);
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "BLOB", base64));
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.DATE);
        } else {
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", x.toString()));
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.TIME);
        } else {
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", x.toString()));
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.TIMESTAMP);
        } else {
            parameters.put(parameterIndex, new Protocol.Parameter(parameterIndex, "TEXT", x.toString()));
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void clearParameters() throws SQLException {
        checkClosed();
        parameters.clear();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        checkClosed();
        if (x == null) {
            setNull(parameterIndex, Types.NULL);
        } else if (x instanceof String) {
            setString(parameterIndex, (String) x);
        } else if (x instanceof Integer) {
            setInt(parameterIndex, (Integer) x);
        } else if (x instanceof Long) {
            setLong(parameterIndex, (Long) x);
        } else if (x instanceof Double) {
            setDouble(parameterIndex, (Double) x);
        } else if (x instanceof Float) {
            setFloat(parameterIndex, (Float) x);
        } else if (x instanceof Boolean) {
            setBoolean(parameterIndex, (Boolean) x);
        } else if (x instanceof byte[]) {
            setBytes(parameterIndex, (byte[]) x);
        } else if (x instanceof BigDecimal) {
            setBigDecimal(parameterIndex, (BigDecimal) x);
        } else if (x instanceof java.sql.Date) {
            setDate(parameterIndex, (java.sql.Date) x);
        } else if (x instanceof Time) {
            setTime(parameterIndex, (Time) x);
        } else if (x instanceof Timestamp) {
            setTimestamp(parameterIndex, (Timestamp) x);
        } else {
            setString(parameterIndex, x.toString());
        }
    }

    @Override
    public boolean execute() throws SQLException {
        checkClosed();
        if (isQuery()) {
            executeQuery();
            return true;
        } else {
            executeUpdate();
            return false;
        }
    }

    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        batchParamSets.add(getParameterList());
        parameters.clear();
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        batchParamSets.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        if (batchParamSets.isEmpty()) {
            return new int[0];
        }
        try {
            Protocol.StatementBatchResponse response = httpClient.executePreparedBatch(statementId, batchParamSets);
            int[] results = new int[response.updateCounts.size()];
            for (int i = 0; i < response.updateCounts.size(); i++) {
                results[i] = response.updateCounts.get(i);
            }
            return results;
        } finally {
            batchParamSets.clear();
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, Types.BLOB);
        } else {
            setBytes(parameterIndex, x.getBytes(1, (int) x.length()));
        }
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, Types.CLOB);
        } else {
            setString(parameterIndex, x.getSubString(1, (int) x.length()));
        }
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        // Fetch metadata from server
        Protocol.StatementMetadataResponse response = httpClient.getStatementMetadata(statementId);
        if (response.columns == null || response.columns.isEmpty()) {
            return null;
        }
        return new ClouderbyResultSetMetaData(response.columns);
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        setDate(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        setTime(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        setTimestamp(parameterIndex, x);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        setNull(parameterIndex, sqlType);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, Types.VARCHAR);
        } else {
            setString(parameterIndex, x.toString());
        }
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        checkClosed();
        return new ClouderbyParameterMetaData(paramCount);
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        setString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        if (value == null) {
            setNull(parameterIndex, Types.NCLOB);
        } else {
            setString(parameterIndex, value.getSubString(1, (int) value.length()));
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            try {
                httpClient.closeStatement(statementId);
            } catch (SQLException e) {
                // Ignore errors on close
            }
            super.close();
        }
    }
}
