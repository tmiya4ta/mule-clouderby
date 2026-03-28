package io.gitlab.myst3m.clouderby.jdbc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;

/**
 * HTTP client for communicating with clouderbyd server.
 * Supports both HTTP and HTTPS connections.
 */
public class HttpClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final java.net.http.HttpClient client;
    private final boolean debug;
    private String sessionId;

    /**
     * Create HTTP client (non-secure).
     */
    public HttpClient(String host, int port) {
        this(host, port, false, false);
    }

    /**
     * Create HTTP client with optional HTTPS support.
     */
    public HttpClient(String host, int port, boolean secure) {
        this(host, port, secure, false);
    }

    /**
     * Create HTTP client with optional HTTPS and debug support.
     *
     * @param host   Server hostname
     * @param port   Server port
     * @param secure Use HTTPS if true
     * @param debug  Enable debug logging if true
     */
    public HttpClient(String host, int port, boolean secure, boolean debug) {
        this.debug = debug;
        String protocol = secure ? "https" : "http";
        // For HTTPS on standard port 443, omit port from URL
        if (secure && port == 443) {
            this.baseUrl = protocol + "://" + host;
        } else {
            this.baseUrl = protocol + "://" + host + ":" + port;
        }
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        if (debug) {
            debug("HttpClient created: " + this.baseUrl);
        }
    }

    private void debug(String message) {
        if (debug) {
            System.err.println("[CLOUDERBY DEBUG] " + message);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // POST /sessions
    public Protocol.OpenResponse openConnection(String database) throws SQLException {
        return openConnection(database, null, null);
    }

    // POST /sessions with authentication
    public Protocol.OpenResponse openConnection(String database, String user, String password) throws SQLException {
        debug(">>> POST /sessions  database=" + database + ", user=" + user);
        try {
            var request = new Protocol.OpenRequest(database, user, password);
            var response = post("/sessions", request, Protocol.OpenResponse.class, false);
            if (response.error != null) {
                debug("<<< POST /sessions  ERROR: " + response.error);
                throw new SQLException("Failed to open connection: " + response.error);
            }
            this.sessionId = response.sessionId;
            debug("<<< POST /sessions  sessionId=" + response.sessionId + ", serverVersion=" + response.serverVersion);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /sessions  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to connect to server: " + e.getMessage(), e);
        }
    }

    // DELETE /sessions
    public Protocol.CloseResponse closeConnection() throws SQLException {
        debug(">>> DELETE /sessions  sessionId=" + sessionId);
        try {
            var response = delete("/sessions", Protocol.CloseResponse.class);
            debug("<<< DELETE /sessions  closed=" + response.closed);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< DELETE /sessions  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to close connection: " + e.getMessage(), e);
        }
    }

    // POST /queries
    public Protocol.QueryResponse executeQuery(String sql, int fetchSize) throws SQLException {
        debug(">>> POST /queries  [QUERY] sql=" + sql + ", fetchSize=" + fetchSize);
        try {
            var request = new Protocol.ExecuteRequest(sql, fetchSize);
            var response = post("/queries", request, Protocol.QueryResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /queries  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /queries  columns=" + (response.columns != null ? response.columns.size() : 0)
                + ", rows=" + (response.rows != null ? response.rows.size() : 0) + ", done=" + response.done);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /queries  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    // POST /queries
    public Protocol.UpdateResponse executeUpdate(String sql) throws SQLException {
        debug(">>> POST /queries  [UPDATE] sql=" + sql);
        try {
            var request = new Protocol.ExecuteRequest(sql);
            var response = post("/queries", request, Protocol.UpdateResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /queries  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /queries  updateCount=" + response.updateCount + ", lastInsertId=" + response.lastInsertId);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /queries  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute update: " + e.getMessage(), e);
        }
    }

    // POST /statements
    public Protocol.PrepareResponse prepareStatement(String sql) throws SQLException {
        debug(">>> POST /statements  [PREPARE] sql=" + sql);
        try {
            var request = new Protocol.PrepareRequest(sql);
            var response = post("/statements", request, Protocol.PrepareResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /statements  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /statements  statementId=" + response.statementId + ", paramCount=" + response.paramCount);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /statements  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to prepare statement: " + e.getMessage(), e);
        }
    }

    // POST /statements/{id}/execute
    public Protocol.QueryResponse executePreparedQuery(String statementId,
                                                        java.util.List<Protocol.Parameter> params,
                                                        int fetchSize) throws SQLException {
        debug(">>> POST /statements/" + statementId + "/execute  [PREPARED QUERY] params=" + formatParams(params) + ", fetchSize=" + fetchSize);
        try {
            var request = new Protocol.StatementExecuteRequest(params, true);
            request.fetchSize = fetchSize;
            var response = post("/statements/" + statementId + "/execute", request, Protocol.QueryResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /statements/" + statementId + "/execute  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /statements/" + statementId + "/execute  columns=" + (response.columns != null ? response.columns.size() : 0)
                + ", rows=" + (response.rows != null ? response.rows.size() : 0) + ", done=" + response.done);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /statements/" + statementId + "/execute  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute prepared query: " + e.getMessage(), e);
        }
    }

    // POST /statements/{id}/execute
    public Protocol.UpdateResponse executePreparedUpdate(String statementId,
                                                          java.util.List<Protocol.Parameter> params) throws SQLException {
        debug(">>> POST /statements/" + statementId + "/execute  [PREPARED UPDATE] params=" + formatParams(params));
        try {
            var request = new Protocol.StatementExecuteRequest(params, false);
            var response = post("/statements/" + statementId + "/execute", request, Protocol.UpdateResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /statements/" + statementId + "/execute  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /statements/" + statementId + "/execute  updateCount=" + response.updateCount + ", lastInsertId=" + response.lastInsertId);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /statements/" + statementId + "/execute  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute prepared update: " + e.getMessage(), e);
        }
    }

    // DELETE /statements/{id}
    public void closeStatement(String statementId) throws SQLException {
        debug(">>> DELETE /statements/" + statementId);
        try {
            delete("/statements/" + statementId, Protocol.CloseResponse.class);
            debug("<<< DELETE /statements/" + statementId + "  OK");
        } catch (IOException | InterruptedException e) {
            debug("<<< DELETE /statements/" + statementId + "  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to close statement: " + e.getMessage(), e);
        }
    }

    // GET /statements/{id}/metadata
    public Protocol.StatementMetadataResponse getStatementMetadata(String statementId) throws SQLException {
        debug(">>> GET /statements/" + statementId + "/metadata");
        try {
            var response = get("/statements/" + statementId + "/metadata", Protocol.StatementMetadataResponse.class);
            if (response.error != null) {
                debug("<<< GET /statements/" + statementId + "/metadata  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< GET /statements/" + statementId + "/metadata  columns=" + (response.columns != null ? response.columns.size() : 0));
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /statements/" + statementId + "/metadata  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to get statement metadata: " + e.getMessage(), e);
        }
    }

    // POST /statements/{id}/batch
    public Protocol.StatementBatchResponse executePreparedBatch(String statementId,
                                                                 java.util.List<java.util.List<Protocol.Parameter>> paramSets) throws SQLException {
        debug(">>> POST /statements/" + statementId + "/batch  [BATCH] sets=" + paramSets.size()
            + (paramSets.isEmpty() ? "" : ", firstSet=" + formatParams(paramSets.get(0))));
        try {
            var request = new Protocol.StatementBatchRequest(paramSets);
            var response = post("/statements/" + statementId + "/batch", request, Protocol.StatementBatchResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /statements/" + statementId + "/batch  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /statements/" + statementId + "/batch  updateCounts=" + response.updateCounts);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /statements/" + statementId + "/batch  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute batch: " + e.getMessage(), e);
        }
    }

    // POST /cursors/{id}/fetch
    public Protocol.QueryResponse fetchCursor(String cursorId, int fetchSize) throws SQLException {
        debug(">>> POST /cursors/" + cursorId + "/fetch  fetchSize=" + fetchSize);
        try {
            var request = new Protocol.CursorFetchRequest(fetchSize);
            var response = post("/cursors/" + cursorId + "/fetch", request, Protocol.QueryResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /cursors/" + cursorId + "/fetch  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< POST /cursors/" + cursorId + "/fetch  rows=" + (response.rows != null ? response.rows.size() : 0)
                + ", done=" + response.done + ", cursorId=" + response.cursorId);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /cursors/" + cursorId + "/fetch  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to fetch cursor: " + e.getMessage(), e);
        }
    }

    // DELETE /cursors/{id}
    public void closeCursor(String cursorId) throws SQLException {
        debug(">>> DELETE /cursors/" + cursorId);
        try {
            delete("/cursors/" + cursorId, Protocol.CloseResponse.class);
            debug("<<< DELETE /cursors/" + cursorId + "  OK");
        } catch (IOException | InterruptedException e) {
            debug("<<< DELETE /cursors/" + cursorId + "  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to close cursor: " + e.getMessage(), e);
        }
    }

    // POST /queries (generic execute)
    public Protocol.ExecuteResponse execute(String sql, int fetchSize) throws SQLException {
        debug(">>> POST /queries  [EXECUTE] sql=" + sql + ", fetchSize=" + fetchSize);
        try {
            var request = new Protocol.ExecuteRequest(sql, fetchSize);
            var response = post("/queries", request, Protocol.ExecuteResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /queries  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            if (response.isQuery()) {
                debug("<<< POST /queries  [RESULT] columns=" + (response.columns != null ? response.columns.size() : 0)
                    + ", rows=" + (response.rows != null ? response.rows.size() : 0));
            } else {
                debug("<<< POST /queries  [RESULT] updateCount=" + response.updateCount + ", lastInsertId=" + response.lastInsertId);
            }
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /queries  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to execute: " + e.getMessage(), e);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType, boolean includeSession)
            throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json");

        if (includeSession && sessionId != null) {
            requestBuilder.header("X-Clouderby-Session-Id", sessionId);
        }

        String jsonBody = body != null ? mapper.writeValueAsString(body) : "{}";
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), responseType);
    }

    private <T> T delete(String path, Class<T> responseType)
            throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json");

        if (sessionId != null) {
            requestBuilder.header("X-Clouderby-Session-Id", sessionId);
        }

        requestBuilder.DELETE();

        var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), responseType);
    }

    private <T> T get(String path, Class<T> responseType)
            throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT);

        if (sessionId != null) {
            requestBuilder.header("X-Clouderby-Session-Id", sessionId);
        }

        requestBuilder.GET();

        var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), responseType);
    }

    // GET /health
    public Protocol.HealthResponse health() throws SQLException {
        debug(">>> GET /health");
        try {
            var response = get("/health", Protocol.HealthResponse.class);
            debug("<<< GET /health  status=" + response.status + ", sessions=" + response.sessions);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /health  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to check health: " + e.getMessage(), e);
        }
    }

    // GET /metadata/info
    public Protocol.MetadataInfoResponse getMetadataInfo() throws SQLException {
        debug(">>> GET /metadata/info");
        try {
            var response = get("/metadata/info", Protocol.MetadataInfoResponse.class);
            if (response.error != null) {
                debug("<<< GET /metadata/info  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< GET /metadata/info  product=" + response.productName + " " + response.productVersion);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /metadata/info  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to get metadata info: " + e.getMessage(), e);
        }
    }

    // GET /metadata/tables
    public Protocol.MetadataTablesResponse getMetadataTables(String catalog, String schema,
                                                              String tablePattern, String[] types) throws SQLException {
        debug(">>> GET /metadata/tables  catalog=" + catalog + ", schema=" + schema
            + ", tablePattern=" + tablePattern + ", types=" + (types != null ? java.util.Arrays.toString(types) : "null"));
        try {
            StringBuilder path = new StringBuilder("/metadata/tables?");
            if (catalog != null) path.append("catalog=").append(urlEncode(catalog)).append("&");
            if (schema != null) path.append("schema=").append(urlEncode(schema)).append("&");
            if (tablePattern != null) path.append("tablePattern=").append(urlEncode(tablePattern)).append("&");
            if (types != null) {
                for (String type : types) {
                    path.append("types=").append(urlEncode(type)).append("&");
                }
            }
            var response = get(path.toString(), Protocol.MetadataTablesResponse.class);
            if (response.error != null) {
                debug("<<< GET /metadata/tables  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< GET /metadata/tables  count=" + (response.tables != null ? response.tables.size() : 0));
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /metadata/tables  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to get metadata tables: " + e.getMessage(), e);
        }
    }

    // GET /metadata/columns
    public Protocol.MetadataColumnsResponse getMetadataColumns(String catalog, String schema,
                                                                String tablePattern, String columnPattern) throws SQLException {
        debug(">>> GET /metadata/columns  catalog=" + catalog + ", schema=" + schema
            + ", tablePattern=" + tablePattern + ", columnPattern=" + columnPattern);
        try {
            StringBuilder path = new StringBuilder("/metadata/columns?");
            if (catalog != null) path.append("catalog=").append(urlEncode(catalog)).append("&");
            if (schema != null) path.append("schema=").append(urlEncode(schema)).append("&");
            if (tablePattern != null) path.append("tablePattern=").append(urlEncode(tablePattern)).append("&");
            if (columnPattern != null) path.append("columnPattern=").append(urlEncode(columnPattern)).append("&");
            var response = get(path.toString(), Protocol.MetadataColumnsResponse.class);
            if (response.error != null) {
                debug("<<< GET /metadata/columns  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< GET /metadata/columns  count=" + (response.columns != null ? response.columns.size() : 0));
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /metadata/columns  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to get metadata columns: " + e.getMessage(), e);
        }
    }

    // GET /metadata/primary-keys
    public Protocol.MetadataPrimaryKeysResponse getMetadataPrimaryKeys(String catalog, String schema,
                                                                        String table) throws SQLException {
        debug(">>> GET /metadata/primary-keys  catalog=" + catalog + ", schema=" + schema + ", table=" + table);
        try {
            StringBuilder path = new StringBuilder("/metadata/primary-keys?");
            if (catalog != null) path.append("catalog=").append(urlEncode(catalog)).append("&");
            if (schema != null) path.append("schema=").append(urlEncode(schema)).append("&");
            if (table != null) path.append("table=").append(urlEncode(table)).append("&");
            var response = get(path.toString(), Protocol.MetadataPrimaryKeysResponse.class);
            if (response.error != null) {
                debug("<<< GET /metadata/primary-keys  ERROR: " + response.error);
                throw new SQLException(response.error);
            }
            debug("<<< GET /metadata/primary-keys  count=" + (response.primaryKeys != null ? response.primaryKeys.size() : 0));
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< GET /metadata/primary-keys  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to get metadata primary keys: " + e.getMessage(), e);
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    // POST /transactions/begin
    public Protocol.TransactionResponse beginTransaction() throws SQLException {
        debug(">>> POST /transactions/begin");
        try {
            var response = post("/transactions/begin", null, Protocol.TransactionResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /transactions/begin  ERROR: " + response.error);
                throw new SQLException("Failed to begin transaction: " + response.error);
            }
            debug("<<< POST /transactions/begin  status=" + response.status);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /transactions/begin  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to begin transaction: " + e.getMessage(), e);
        }
    }

    // POST /transactions/commit
    public Protocol.TransactionResponse commitTransaction() throws SQLException {
        debug(">>> POST /transactions/commit");
        try {
            var response = post("/transactions/commit", null, Protocol.TransactionResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /transactions/commit  ERROR: " + response.error);
                throw new SQLException("Failed to commit transaction: " + response.error);
            }
            debug("<<< POST /transactions/commit  status=" + response.status);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /transactions/commit  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to commit transaction: " + e.getMessage(), e);
        }
    }

    // POST /transactions/rollback
    public Protocol.TransactionResponse rollbackTransaction() throws SQLException {
        debug(">>> POST /transactions/rollback");
        try {
            var response = post("/transactions/rollback", null, Protocol.TransactionResponse.class, true);
            if (response.error != null) {
                debug("<<< POST /transactions/rollback  ERROR: " + response.error);
                throw new SQLException("Failed to rollback transaction: " + response.error);
            }
            debug("<<< POST /transactions/rollback  status=" + response.status);
            return response;
        } catch (IOException | InterruptedException e) {
            debug("<<< POST /transactions/rollback  EXCEPTION: " + e.getMessage());
            throw new SQLException("Failed to rollback transaction: " + e.getMessage(), e);
        }
    }

    private String formatParams(java.util.List<Protocol.Parameter> params) {
        if (params == null || params.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            var p = params.get(i);
            sb.append(p.index).append(":").append(p.type).append("=").append(p.value);
        }
        sb.append("]");
        return sb.toString();
    }
}
