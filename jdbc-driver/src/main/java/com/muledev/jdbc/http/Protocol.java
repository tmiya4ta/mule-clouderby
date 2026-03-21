package com.muledev.jdbc.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Protocol message classes for clouderbyd HTTP API.
 */
public class Protocol {

    // Request classes

    public static class OpenRequest {
        public String database;
        public String user;
        public String password;

        public OpenRequest(String database) {
            this(database, null, null);
        }

        public OpenRequest(String database, String user, String password) {
            this.database = database;
            this.user = user;
            this.password = password;
        }
    }

    public static class ExecuteRequest {
        public String sql;
        @JsonProperty("fetch-size")
        public int fetchSize = 100;

        public ExecuteRequest(String sql) {
            this.sql = sql;
        }

        public ExecuteRequest(String sql, int fetchSize) {
            this.sql = sql;
            this.fetchSize = fetchSize;
        }
    }

    public static class PrepareRequest {
        public String sql;

        public PrepareRequest(String sql) {
            this.sql = sql;
        }
    }

    public static class StatementExecuteRequest {
        public List<Parameter> params;
        public boolean query;
        @JsonProperty("fetch-size")
        public int fetchSize = 100;

        public StatementExecuteRequest(List<Parameter> params, boolean query) {
            this.params = params;
            this.query = query;
        }
    }

    public static class Parameter {
        public int index;
        public String type;
        public Object value;

        public Parameter(int index, String type, Object value) {
            this.index = index;
            this.type = type;
            this.value = value;
        }
    }


    // Response classes

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenResponse {
        @JsonProperty("session-id")
        public String sessionId;
        @JsonProperty("server-version")
        public String serverVersion;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloseResponse {
        public boolean closed;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnInfo {
        public String name;
        public String type;
        @JsonProperty("table-name")
        public String tableName;
        public int nullable = 2; // 0=NO, 1=YES, 2=UNKNOWN (columnNullableUnknown)
        @JsonProperty("auto-increment")
        public boolean autoIncrement;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryResponse {
        public List<ColumnInfo> columns;
        public List<List<Object>> rows;
        public boolean done;
        @JsonProperty("cursor-id")
        public String cursorId;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateResponse {
        @JsonProperty("update-count")
        public int updateCount;
        @JsonProperty("last-insert-id")
        public Long lastInsertId;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrepareResponse {
        @JsonProperty("statement-id")
        public String statementId;
        @JsonProperty("param-count")
        public int paramCount;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealthResponse {
        public String status;
        public String version;
        public int sessions;
    }

    /**
     * Generic execute response that can contain either query or update results.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExecuteResponse {
        // Query result fields
        public List<ColumnInfo> columns;
        public List<List<Object>> rows;
        public Boolean done;
        @JsonProperty("cursor-id")
        public String cursorId;

        // Update result fields
        @JsonProperty("update-count")
        public Integer updateCount;
        @JsonProperty("last-insert-id")
        public Long lastInsertId;

        public String error;

        public boolean isQuery() {
            return columns != null;
        }
    }

    // Statement metadata response
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatementMetadataResponse {
        public List<ColumnInfo> columns;
        public String error;
    }

    // Cursor fetch request
    public static class CursorFetchRequest {
        @JsonProperty("fetch-size")
        public int fetchSize;

        public CursorFetchRequest(int fetchSize) {
            this.fetchSize = fetchSize;
        }
    }

    // Batch execution for PreparedStatement
    public static class StatementBatchRequest {
        @JsonProperty("param-sets")
        public List<List<Parameter>> paramSets;

        public StatementBatchRequest(List<List<Parameter>> paramSets) {
            this.paramSets = paramSets;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatementBatchResponse {
        @JsonProperty("update-counts")
        public List<Integer> updateCounts;
        public String error;
    }

    // Metadata API types

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataInfoResponse {
        @JsonProperty("product-name")
        public String productName;
        @JsonProperty("product-version")
        public String productVersion;
        @JsonProperty("driver-name")
        public String driverName;
        @JsonProperty("driver-version")
        public String driverVersion;
        @JsonProperty("identifier-quote-string")
        public String identifierQuoteString;
        @JsonProperty("catalog-separator")
        public String catalogSeparator;
        @JsonProperty("catalog-term")
        public String catalogTerm;
        @JsonProperty("schema-term")
        public String schemaTerm;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataTablesResponse {
        public List<TableInfo> tables;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableInfo {
        public String catalog;
        public String schema;
        public String name;
        public String type;
        public String remarks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataColumnsResponse {
        public List<ColumnMetadata> columns;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnMetadata {
        public String catalog;
        public String schema;
        @JsonProperty("table-name")
        public String tableName;
        @JsonProperty("column-name")
        public String columnName;
        @JsonProperty("data-type")
        public int dataType;
        @JsonProperty("type-name")
        public String typeName;
        @JsonProperty("column-size")
        public int columnSize;
        public int nullable;
        public String remarks;
        @JsonProperty("default-value")
        public String defaultValue;
        @JsonProperty("ordinal-position")
        public int ordinalPosition;
        @JsonProperty("is-auto-increment")
        public boolean isAutoIncrement;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataPrimaryKeysResponse {
        @JsonProperty("primary-keys")
        public List<PrimaryKeyInfo> primaryKeys;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrimaryKeyInfo {
        public String catalog;
        public String schema;
        @JsonProperty("table-name")
        public String tableName;
        @JsonProperty("column-name")
        public String columnName;
        @JsonProperty("key-seq")
        public int keySeq;
        @JsonProperty("pk-name")
        public String pkName;
    }

    // Transaction API types

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionResponse {
        public String status;
        @JsonProperty("in-transaction")
        public boolean inTransaction;
        public String error;
    }
}
