package io.gitlab.myst3m.clouderby.jdbc.http;

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
        public int fetchSize = 1000;

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
        public int fetchSize = 1000;

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

    /**
     * Base for responses that may carry an error.
     *
     * Spec ErrorResponse uses {error: "message string"}. Some servers (the Mule
     * reference) instead return {error: true, message: "..."}. This base accepts
     * either shape and surfaces a single string via {@link #errorMessage()}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorBase {
        private String errorMessage;

        @JsonProperty("error")
        public void setError(Object value) {
            if (value == null) return;
            if (value instanceof Boolean) {
                if ((Boolean) value && errorMessage == null) {
                    errorMessage = "Server returned error";
                }
            } else {
                String s = String.valueOf(value);
                if (!s.isEmpty()) errorMessage = s;
            }
        }

        @JsonProperty("message")
        public void setMessage(String message) {
            if (message != null) errorMessage = message;
        }

        public String errorMessage() { return errorMessage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenResponse extends ErrorBase {
        @JsonProperty("session-id")
        public String sessionId;
        @JsonProperty("server-version")
        public String serverVersion;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloseResponse extends ErrorBase {
        public boolean closed;
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
    public static class QueryResponse extends ErrorBase {
        public List<ColumnInfo> columns;
        public List<List<Object>> rows;
        public boolean done;
        @JsonProperty("cursor-id")
        public String cursorId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateResponse extends ErrorBase {
        @JsonProperty("update-count")
        public int updateCount;
        @JsonProperty("last-insert-id")
        public Long lastInsertId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrepareResponse extends ErrorBase {
        @JsonProperty("statement-id")
        public String statementId;
        @JsonProperty("param-count")
        public int paramCount;
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
    public static class ExecuteResponse extends ErrorBase {
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

        public boolean isQuery() {
            return columns != null;
        }
    }

    // Statement metadata response
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatementMetadataResponse extends ErrorBase {
        public List<ColumnInfo> columns;
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
    public static class StatementBatchResponse extends ErrorBase {
        @JsonProperty("update-counts")
        public List<Integer> updateCounts;
    }

    // Metadata API types

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataInfoResponse extends ErrorBase {
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataTablesResponse extends ErrorBase {
        public List<TableInfo> tables;
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
    public static class MetadataColumnsResponse extends ErrorBase {
        public List<ColumnMetadata> columns;
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
    public static class MetadataPrimaryKeysResponse extends ErrorBase {
        @JsonProperty("primary-keys")
        public List<PrimaryKeyInfo> primaryKeys;
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
    public static class TransactionResponse extends ErrorBase {
        public String status;
        @JsonProperty("in-transaction")
        public boolean inTransaction;
    }
}
