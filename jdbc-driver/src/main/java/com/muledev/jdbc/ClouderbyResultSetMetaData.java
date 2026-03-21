package com.muledev.jdbc;

import com.muledev.jdbc.http.Protocol;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * JDBC ResultSetMetaData implementation for clouderbyd.
 */
public class ClouderbyResultSetMetaData implements ResultSetMetaData {
    private final List<Protocol.ColumnInfo> columns;

    public ClouderbyResultSetMetaData(List<Protocol.ColumnInfo> columns) {
        this.columns = columns != null ? columns : List.of();
    }

    private void checkIndex(int column) throws SQLException {
        if (column < 1 || column > columns.size()) {
            throw new SQLException("Column index out of range: " + column);
        }
    }

    @Override
    public int getColumnCount() throws SQLException {
        return columns.size();
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        checkIndex(column);
        return columns.get(column - 1).autoIncrement;
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        checkIndex(column);
        return true;
    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
        checkIndex(column);
        return true;
    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
        checkIndex(column);
        return false;
    }

    @Override
    public int isNullable(int column) throws SQLException {
        checkIndex(column);
        return columns.get(column - 1).nullable;
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
        checkIndex(column);
        String type = columns.get(column - 1).type;
        return "INTEGER".equals(type) || "REAL".equals(type);
    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        checkIndex(column);
        return 255;
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        checkIndex(column);
        return columns.get(column - 1).name;
    }

    @Override
    public String getSchemaName(int column) throws SQLException {
        checkIndex(column);
        return "";
    }

    @Override
    public int getPrecision(int column) throws SQLException {
        checkIndex(column);
        return 0;
    }

    @Override
    public int getScale(int column) throws SQLException {
        checkIndex(column);
        return 0;
    }

    @Override
    public String getTableName(int column) throws SQLException {
        checkIndex(column);
        String tableName = columns.get(column - 1).tableName;
        return tableName != null ? tableName : "";
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
        checkIndex(column);
        return "";
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        checkIndex(column);
        String type = columns.get(column - 1).type;
        return switch (type) {
            case "INTEGER" -> Types.BIGINT;
            case "REAL" -> Types.DOUBLE;
            case "BLOB" -> Types.BLOB;
            case "NULL" -> Types.NULL;
            default -> Types.VARCHAR;
        };
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        checkIndex(column);
        return columns.get(column - 1).type;
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
        checkIndex(column);
        return false;
    }

    @Override
    public boolean isWritable(int column) throws SQLException {
        checkIndex(column);
        return true;
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        checkIndex(column);
        return true;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        checkIndex(column);
        String type = columns.get(column - 1).type;
        return switch (type) {
            case "INTEGER" -> Long.class.getName();
            case "REAL" -> Double.class.getName();
            case "BLOB" -> byte[].class.getName();
            default -> String.class.getName();
        };
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
