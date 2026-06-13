package io.gitlab.myst3m.clouderby.jdbc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * HTTP client for communicating with clouderbyd server.
 * Supports both HTTP and HTTPS connections.
 */
public class HttpClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final byte[] EMPTY_OBJECT = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    /**
     * One JVM-wide HTTP client shared by all connections. java.net.http.HttpClient
     * is thread-safe and pools keep-alive connections per origin, so sharing it lets
     * a new JDBC Connection reuse an already-established TCP/TLS connection instead
     * of paying a fresh handshake.
     */
    private static final java.net.http.HttpClient SHARED_CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final String baseUrl;
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
        if (debug) {
            debug("HttpClient created: " + this.baseUrl);
        }
    }

    private void debug(String message) {
        System.err.println("[CLOUDERBY DEBUG] " + message);
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

    // ===== error-handling helpers =====

    @FunctionalInterface
    private interface HttpCall<T> {
        T run() throws IOException, InterruptedException;
    }

    /** Wrap transport-level failures into SQLException("Failed to <action>: ..."). */
    private <T> T wrap(String action, HttpCall<T> call) throws SQLException {
        try {
            return call.run();
        } catch (IOException | InterruptedException e) {
            if (debug) debug("<<< EXCEPTION (" + action + "): " + e.getMessage());
            throw new SQLException("Failed to " + action + ": " + e.getMessage(), e);
        }
    }

    /** Throw if the server reported an error; prefix is prepended when non-null. */
    private <T extends Protocol.ErrorBase> T checkError(T response, String prefix) throws SQLException {
        String err = response.errorMessage();
        if (err != null) {
            if (debug) debug("<<< SERVER ERROR: " + err);
            throw new SQLException(prefix != null ? prefix + err : err);
        }
        return response;
    }

    // ===== endpoint methods =====

    // POST /sessions
    public Protocol.OpenResponse openConnection(String database) throws SQLException {
        return openConnection(database, null, null);
    }

    // POST /sessions with authentication
    public Protocol.OpenResponse openConnection(String database, String user, String password) throws SQLException {
        if (debug) debug(">>> POST /sessions  database=" + database + ", user=" + user);
        var response = checkError(
                wrap("connect to server", () ->
                        post("/sessions", new Protocol.OpenRequest(database, user, password),
                             Protocol.OpenResponse.class, false)),
                "Failed to open connection: ");
        this.sessionId = response.sessionId;
        if (debug) debug("<<< POST /sessions  sessionId=" + response.sessionId
                + ", serverVersion=" + response.serverVersion);
        return response;
    }

    // DELETE /sessions
    public Protocol.CloseResponse closeConnection() throws SQLException {
        if (debug) debug(">>> DELETE /sessions  sessionId=" + sessionId);
        var response = wrap("close connection", () ->
                delete("/sessions", Protocol.CloseResponse.class));
        if (debug) debug("<<< DELETE /sessions  closed=" + response.closed);
        return response;
    }

    // POST /queries
    public Protocol.QueryResponse executeQuery(String sql, int fetchSize) throws SQLException {
        if (debug) debug(">>> POST /queries  [QUERY] sql=" + sql + ", fetchSize=" + fetchSize);
        var response = checkError(
                wrap("execute query", () ->
                        post("/queries", new Protocol.ExecuteRequest(sql, fetchSize),
                             Protocol.QueryResponse.class, true)),
                null);
        if (debug) debug("<<< POST /queries  columns=" + sizeOf(response.columns)
                + ", rows=" + sizeOf(response.rows) + ", done=" + response.done);
        return response;
    }

    // POST /queries
    public Protocol.UpdateResponse executeUpdate(String sql) throws SQLException {
        if (debug) debug(">>> POST /queries  [UPDATE] sql=" + sql);
        var response = checkError(
                wrap("execute update", () ->
                        post("/queries", new Protocol.ExecuteRequest(sql),
                             Protocol.UpdateResponse.class, true)),
                null);
        if (debug) debug("<<< POST /queries  updateCount=" + response.updateCount
                + ", lastInsertId=" + response.lastInsertId);
        return response;
    }

    // POST /queries (generic execute)
    public Protocol.ExecuteResponse execute(String sql, int fetchSize) throws SQLException {
        if (debug) debug(">>> POST /queries  [EXECUTE] sql=" + sql + ", fetchSize=" + fetchSize);
        var response = checkError(
                wrap("execute", () ->
                        post("/queries", new Protocol.ExecuteRequest(sql, fetchSize),
                             Protocol.ExecuteResponse.class, true)),
                null);
        if (debug) {
            if (response.isQuery()) {
                debug("<<< POST /queries  [RESULT] columns=" + sizeOf(response.columns)
                        + ", rows=" + sizeOf(response.rows));
            } else {
                debug("<<< POST /queries  [RESULT] updateCount=" + response.updateCount
                        + ", lastInsertId=" + response.lastInsertId);
            }
        }
        return response;
    }

    // POST /statements
    public Protocol.PrepareResponse prepareStatement(String sql) throws SQLException {
        if (debug) debug(">>> POST /statements  [PREPARE] sql=" + sql);
        var response = checkError(
                wrap("prepare statement", () ->
                        post("/statements", new Protocol.PrepareRequest(sql),
                             Protocol.PrepareResponse.class, true)),
                null);
        if (debug) debug("<<< POST /statements  statementId=" + response.statementId
                + ", paramCount=" + response.paramCount);
        return response;
    }

    // POST /statements/{id}/execute
    public Protocol.QueryResponse executePreparedQuery(String statementId,
                                                        List<Protocol.Parameter> params,
                                                        int fetchSize) throws SQLException {
        if (debug) debug(">>> POST /statements/" + statementId + "/execute  [PREPARED QUERY] params="
                + formatParams(params) + ", fetchSize=" + fetchSize);
        var request = new Protocol.StatementExecuteRequest(params, true);
        request.fetchSize = fetchSize;
        var response = checkError(
                wrap("execute prepared query", () ->
                        post("/statements/" + statementId + "/execute", request,
                             Protocol.QueryResponse.class, true)),
                null);
        if (debug) debug("<<< POST /statements/" + statementId + "/execute  columns=" + sizeOf(response.columns)
                + ", rows=" + sizeOf(response.rows) + ", done=" + response.done);
        return response;
    }

    // POST /statements/{id}/execute
    public Protocol.UpdateResponse executePreparedUpdate(String statementId,
                                                          List<Protocol.Parameter> params) throws SQLException {
        if (debug) debug(">>> POST /statements/" + statementId + "/execute  [PREPARED UPDATE] params="
                + formatParams(params));
        var response = checkError(
                wrap("execute prepared update", () ->
                        post("/statements/" + statementId + "/execute",
                             new Protocol.StatementExecuteRequest(params, false),
                             Protocol.UpdateResponse.class, true)),
                null);
        if (debug) debug("<<< POST /statements/" + statementId + "/execute  updateCount=" + response.updateCount
                + ", lastInsertId=" + response.lastInsertId);
        return response;
    }

    // POST /statements/{id}/batch
    public Protocol.StatementBatchResponse executePreparedBatch(String statementId,
                                                                 List<List<Protocol.Parameter>> paramSets) throws SQLException {
        if (debug) debug(">>> POST /statements/" + statementId + "/batch  [BATCH] sets=" + paramSets.size()
                + (paramSets.isEmpty() ? "" : ", firstSet=" + formatParams(paramSets.get(0))));
        var response = checkError(
                wrap("execute batch", () ->
                        post("/statements/" + statementId + "/batch",
                             new Protocol.StatementBatchRequest(paramSets),
                             Protocol.StatementBatchResponse.class, true)),
                null);
        if (debug) debug("<<< POST /statements/" + statementId + "/batch  updateCounts=" + response.updateCounts);
        return response;
    }

    // GET /statements/{id}/metadata
    public Protocol.StatementMetadataResponse getStatementMetadata(String statementId) throws SQLException {
        if (debug) debug(">>> GET /statements/" + statementId + "/metadata");
        var response = checkError(
                wrap("get statement metadata", () ->
                        get("/statements/" + statementId + "/metadata",
                            Protocol.StatementMetadataResponse.class)),
                null);
        if (debug) debug("<<< GET /statements/" + statementId + "/metadata  columns=" + sizeOf(response.columns));
        return response;
    }

    // DELETE /statements/{id}
    public void closeStatement(String statementId) throws SQLException {
        if (debug) debug(">>> DELETE /statements/" + statementId);
        wrap("close statement", () -> delete("/statements/" + statementId, Protocol.CloseResponse.class));
        if (debug) debug("<<< DELETE /statements/" + statementId + "  OK");
    }

    // POST /cursors/{id}/fetch
    public Protocol.QueryResponse fetchCursor(String cursorId, int fetchSize) throws SQLException {
        if (debug) debug(">>> POST /cursors/" + cursorId + "/fetch  fetchSize=" + fetchSize);
        var response = checkError(
                wrap("fetch cursor", () ->
                        post("/cursors/" + cursorId + "/fetch", new Protocol.CursorFetchRequest(fetchSize),
                             Protocol.QueryResponse.class, true)),
                null);
        if (debug) debug("<<< POST /cursors/" + cursorId + "/fetch  rows=" + sizeOf(response.rows)
                + ", done=" + response.done + ", cursorId=" + response.cursorId);
        return response;
    }

    // DELETE /cursors/{id}
    public void closeCursor(String cursorId) throws SQLException {
        if (debug) debug(">>> DELETE /cursors/" + cursorId);
        wrap("close cursor", () -> delete("/cursors/" + cursorId, Protocol.CloseResponse.class));
        if (debug) debug("<<< DELETE /cursors/" + cursorId + "  OK");
    }

    // GET /health
    public Protocol.HealthResponse health() throws SQLException {
        if (debug) debug(">>> GET /health");
        var response = wrap("check health", () -> get("/health", Protocol.HealthResponse.class));
        if (debug) debug("<<< GET /health  status=" + response.status + ", sessions=" + response.sessions);
        return response;
    }

    // GET /metadata/info
    public Protocol.MetadataInfoResponse getMetadataInfo() throws SQLException {
        if (debug) debug(">>> GET /metadata/info");
        var response = checkError(
                wrap("get metadata info", () ->
                        get("/metadata/info", Protocol.MetadataInfoResponse.class)),
                null);
        if (debug) debug("<<< GET /metadata/info  product=" + response.productName + " " + response.productVersion);
        return response;
    }

    // GET /metadata/tables
    public Protocol.MetadataTablesResponse getMetadataTables(String catalog, String schema,
                                                              String tablePattern, String[] types) throws SQLException {
        if (debug) debug(">>> GET /metadata/tables  catalog=" + catalog + ", schema=" + schema
                + ", tablePattern=" + tablePattern + ", types=" + (types != null ? java.util.Arrays.toString(types) : "null"));
        StringBuilder path = new StringBuilder("/metadata/tables?");
        appendParam(path, "catalog", catalog);
        appendParam(path, "schema", schema);
        appendParam(path, "tablePattern", tablePattern);
        if (types != null) {
            for (String type : types) {
                appendParam(path, "types", type);
            }
        }
        var response = checkError(
                wrap("get metadata tables", () ->
                        get(path.toString(), Protocol.MetadataTablesResponse.class)),
                null);
        if (debug) debug("<<< GET /metadata/tables  count=" + sizeOf(response.tables));
        return response;
    }

    // GET /metadata/columns
    public Protocol.MetadataColumnsResponse getMetadataColumns(String catalog, String schema,
                                                                String tablePattern, String columnPattern) throws SQLException {
        if (debug) debug(">>> GET /metadata/columns  catalog=" + catalog + ", schema=" + schema
                + ", tablePattern=" + tablePattern + ", columnPattern=" + columnPattern);
        StringBuilder path = new StringBuilder("/metadata/columns?");
        appendParam(path, "catalog", catalog);
        appendParam(path, "schema", schema);
        appendParam(path, "tablePattern", tablePattern);
        appendParam(path, "columnPattern", columnPattern);
        var response = checkError(
                wrap("get metadata columns", () ->
                        get(path.toString(), Protocol.MetadataColumnsResponse.class)),
                null);
        if (debug) debug("<<< GET /metadata/columns  count=" + sizeOf(response.columns));
        return response;
    }

    // GET /metadata/primary-keys
    public Protocol.MetadataPrimaryKeysResponse getMetadataPrimaryKeys(String catalog, String schema,
                                                                        String table) throws SQLException {
        if (debug) debug(">>> GET /metadata/primary-keys  catalog=" + catalog + ", schema=" + schema + ", table=" + table);
        StringBuilder path = new StringBuilder("/metadata/primary-keys?");
        appendParam(path, "catalog", catalog);
        appendParam(path, "schema", schema);
        appendParam(path, "table", table);
        var response = checkError(
                wrap("get metadata primary keys", () ->
                        get(path.toString(), Protocol.MetadataPrimaryKeysResponse.class)),
                null);
        if (debug) debug("<<< GET /metadata/primary-keys  count=" + sizeOf(response.primaryKeys));
        return response;
    }

    // POST /transactions/begin
    public Protocol.TransactionResponse beginTransaction() throws SQLException {
        return transaction("begin");
    }

    // POST /transactions/commit
    public Protocol.TransactionResponse commitTransaction() throws SQLException {
        return transaction("commit");
    }

    // POST /transactions/rollback
    public Protocol.TransactionResponse rollbackTransaction() throws SQLException {
        return transaction("rollback");
    }

    private Protocol.TransactionResponse transaction(String op) throws SQLException {
        if (debug) debug(">>> POST /transactions/" + op);
        var response = checkError(
                wrap(op + " transaction", () ->
                        post("/transactions/" + op, null, Protocol.TransactionResponse.class, true)),
                "Failed to " + op + " transaction: ");
        if (debug) debug("<<< POST /transactions/" + op + "  status=" + response.status);
        return response;
    }

    // ===== transport =====

    private static InputStream maybeDecompress(HttpResponse<InputStream> response) throws IOException {
        InputStream in = response.body();
        String enc = response.headers().firstValue("Content-Encoding").orElse("");
        if (enc.equalsIgnoreCase("gzip")) {
            return new GZIPInputStream(in);
        }
        return in;
    }

    private HttpRequest.Builder requestBuilder(String path, boolean includeSession) {
        var rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept-Encoding", "gzip");
        if (includeSession && sessionId != null) {
            rb.header("X-Clouderby-Session-Id", sessionId);
        }
        return rb;
    }

    private <T> T send(HttpRequest request, Class<T> responseType)
            throws IOException, InterruptedException {
        var response = SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var in = maybeDecompress(response)) {
            return mapper.readValue(in, responseType);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType, boolean includeSession)
            throws IOException, InterruptedException {
        byte[] jsonBody = body != null ? mapper.writeValueAsBytes(body) : EMPTY_OBJECT;
        var request = requestBuilder(path, includeSession)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody))
                .build();
        return send(request, responseType);
    }

    private <T> T delete(String path, Class<T> responseType)
            throws IOException, InterruptedException {
        var request = requestBuilder(path, true)
                .header("Content-Type", "application/json")
                .DELETE()
                .build();
        return send(request, responseType);
    }

    private <T> T get(String path, Class<T> responseType)
            throws IOException, InterruptedException {
        var request = requestBuilder(path, true).GET().build();
        return send(request, responseType);
    }

    // ===== formatting helpers =====

    private static void appendParam(StringBuilder path, String name, String value) {
        if (value != null) {
            path.append(name).append('=')
                .append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8))
                .append('&');
        }
    }

    private static int sizeOf(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private static String formatParams(List<Protocol.Parameter> params) {
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
