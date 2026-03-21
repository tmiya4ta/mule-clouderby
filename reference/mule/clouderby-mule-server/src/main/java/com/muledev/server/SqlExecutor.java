package com.muledev.server;

import java.sql.*;
import java.util.*;

/**
 * Static SQL execution methods invoked from Mule flows via java:invoke-static.
 * All public methods return Map&lt;String, Object&gt; for DataWeave JSON conversion.
 */
public class SqlExecutor {

    private static ClouderbySessionManager sessionManager;

    public static void setSessionManager(ClouderbySessionManager mgr) {
        sessionManager = mgr;
    }

    // ========== Session methods ==========

    public static Map<String, Object> createSession(String database) throws Exception {
        ClouderbySessionManager.SessionData session = sessionManager.createSession(database);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session-id", session.getSessionId());
        result.put("server-version", "1.0.0");
        return result;
    }

    public static Map<String, Object> closeSession(String sessionId) throws Exception {
        boolean closed = sessionManager.closeSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", closed);
        return result;
    }

    public static Map<String, Object> validateSession(String sessionId) throws Exception {
        getSessionOrThrow(sessionId);
        return Collections.singletonMap("valid", true);
    }

    // ========== Transaction methods ==========

    public static Map<String, Object> beginTransaction(String sessionId) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        session.beginTransaction();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "STARTED");
        result.put("in-transaction", true);
        return result;
    }

    public static Map<String, Object> commitTransaction(String sessionId) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        session.commitTransaction();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "COMMITTED");
        result.put("in-transaction", false);
        return result;
    }

    public static Map<String, Object> rollbackTransaction(String sessionId) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        session.rollbackTransaction();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ROLLED_BACK");
        result.put("in-transaction", false);
        return result;
    }

    // ========== Statement methods ==========

    public static Map<String, Object> createStatement(String sessionId, String sql) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        String stmtId = session.addStatement(sql);
        int paramCount = countPlaceholders(sql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statement-id", stmtId);
        result.put("param-count", paramCount);
        return result;
    }

    public static Map<String, Object> closeStatement(String sessionId, String stmtId) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        session.removeStatement(stmtId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", true);
        return result;
    }

    // ========== SQL execution methods ==========

    public static Map<String, Object> executeDirectSql(Object sessionId, Object sql, Object fetchSize) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow((String) sessionId);
        int fs = (fetchSize instanceof Number) ? ((Number) fetchSize).intValue() : 100;
        Connection conn = session.getConnection();
        String sqlStr = (String) sql;
        String keyword = getFirstKeyword(sqlStr);

        if ("SELECT".equals(keyword) || "VALUES".equals(keyword) || "WITH".equals(keyword)) {
            return executeQuery(conn, sqlStr, null, fs, (String) sessionId);
        } else if ("CREATE".equals(keyword) || "DROP".equals(keyword) || "ALTER".equals(keyword)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sqlStr);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("update-count", 0);
            return result;
        } else {
            return executeUpdate(conn, sqlStr, null);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> executePrepared(Object sessionId, Object stmtId,
            Object params, Object isQuery, Object fetchSize) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow((String) sessionId);
        String sql = session.getStatementSql((String) stmtId);
        if (sql == null) {
            throw new RuntimeException("STMT_NOT_FOUND:Statement not found: " + stmtId);
        }
        Connection conn = session.getConnection();
        List paramList = (params instanceof List) ? (List) params : null;
        boolean query = (isQuery instanceof Boolean) ? (Boolean) isQuery : false;
        int fs = (fetchSize instanceof Number) ? ((Number) fetchSize).intValue() : 100;

        if (query) {
            return executeQuery(conn, sql, paramList, fs, (String) sessionId);
        } else {
            String keyword = getFirstKeyword(sql);
            if ("CREATE".equals(keyword) || "DROP".equals(keyword) || "ALTER".equals(keyword)) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParameters(ps, paramList);
                    ps.execute();
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("update-count", 0);
                return result;
            }
            return executeUpdate(conn, sql, paramList);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> executeBatch(Object sessionId, Object stmtId, Object paramSets) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow((String) sessionId);
        String sql = session.getStatementSql((String) stmtId);
        if (sql == null) {
            throw new RuntimeException("STMT_NOT_FOUND:Statement not found: " + stmtId);
        }
        Connection conn = session.getConnection();
        List<Integer> updateCounts = new ArrayList<>();

        for (Object paramSet : (List) paramSets) {
            List params = (List) paramSet;
            Map<String, Object> res = executeUpdate(conn, sql, params);
            updateCounts.add((Integer) res.get("update-count"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("update-counts", updateCounts);
        return result;
    }

    // ========== Metadata query (returns List<Map> like db:select) ==========

    /**
     * Execute a SELECT query using the session connection.
     * Returns List&lt;Map&gt; (same format as Mule db:select) for DataWeave compatibility.
     * SQL uses positional ? params, values passed as a simple List.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> executeMetadataQuery(String sessionId, String sql, List params) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow(sessionId);
        return executeSelectAsMaps(session.getConnection(), sql, params);
    }

    /**
     * Execute a SELECT query using the system connection (no session required).
     * Used by UI endpoints that don't have a session header.
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> executeSystemQuery(String sql, List params) throws Exception {
        try (Connection conn = sessionManager.getSystemConnection()) {
            return executeSelectAsMaps(conn, sql, params);
        }
    }

    // ========== Internal helpers ==========

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> executeSelectAsMaps(Connection conn, String sql, List params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeQuery(Connection conn, String sql, List params,
                                                     int fetchSize, String sessionId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        try {
            setParameters(ps, params);
            ps.setFetchSize(fetchSize);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            List<Map<String, Object>> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("name", meta.getColumnName(i));
                col.put("type", meta.getColumnTypeName(i));
                col.put("table-name", meta.getTableName(i) != null ? meta.getTableName(i) : "");
                col.put("nullable", meta.isNullable(i));
                col.put("auto-increment", meta.isAutoIncrement(i));
                columns.add(col);
            }

            List<List<Object>> rows = new ArrayList<>();
            int count = 0;
            while (count < fetchSize && rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
                count++;
            }
            // Peek ahead to check if there are more rows
            boolean hasMore = (count == fetchSize) && rs.next();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("done", !hasMore);

            if (hasMore && sessionId != null) {
                // Keep ResultSet/Statement open as a server-side cursor
                ClouderbySessionManager.SessionData session = sessionManager.getSession(sessionId);
                if (session != null) {
                    ClouderbySessionManager.CursorData cursorData =
                        new ClouderbySessionManager.CursorData(rs, ps, columns, fetchSize);
                    // Save the peeked row so it's not lost
                    List<Object> peekedRow = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        peekedRow.add(rs.getObject(i));
                    }
                    cursorData.pendingRow = peekedRow;
                    String cursorId = session.addCursor(cursorData);
                    result.put("cursor-id", cursorId);
                } else {
                    rs.close();
                    ps.close();
                }
            } else {
                rs.close();
                ps.close();
            }
            return result;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

    // ========== Cursor methods ==========

    /**
     * Fetch next page from an open server-side cursor.
     */
    public static Map<String, Object> fetchCursor(Object sessionId, Object cursorId, Object fetchSize) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow((String) sessionId);
        String cid = (String) cursorId;
        int fs = (fetchSize instanceof Number) ? ((Number) fetchSize).intValue() : 100;

        ClouderbySessionManager.CursorData cursor = session.getCursor(cid);
        if (cursor == null) {
            throw new RuntimeException("CURSOR_NOT_FOUND:Cursor not found: " + cid);
        }

        ResultSet rs = cursor.resultSet;
        int colCount = cursor.columns.size();
        List<List<Object>> rows = new ArrayList<>();
        int count = 0;

        // Prepend the pending row from previous peek-ahead
        if (cursor.pendingRow != null) {
            rows.add(cursor.pendingRow);
            cursor.pendingRow = null;
            count++;
        }

        while (count < fs && rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
            count++;
        }

        // Peek ahead to check if there are more rows
        boolean hasMore = (count == fs) && rs.next();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", cursor.columns);
        result.put("rows", rows);
        result.put("done", !hasMore);

        if (!hasMore) {
            result.put("cursor-id", null);
            session.removeCursor(cid);
        } else {
            // Save the peeked row
            List<Object> peekedRow = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                peekedRow.add(rs.getObject(i));
            }
            cursor.pendingRow = peekedRow;
            result.put("cursor-id", cid);
        }
        return result;
    }

    /**
     * Close an open server-side cursor.
     */
    public static Map<String, Object> closeCursor(Object sessionId, Object cursorId) throws Exception {
        ClouderbySessionManager.SessionData session = getSessionOrThrow((String) sessionId);
        session.removeCursor((String) cursorId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("closed", true);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeUpdate(Connection conn, String sql, List params) throws SQLException {
        String keyword = getFirstKeyword(sql);
        boolean isInsert = "INSERT".equals(keyword);
        try (PreparedStatement ps = isInsert
                ? conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                : conn.prepareStatement(sql)) {
            setParameters(ps, params);
            int affected = ps.executeUpdate();

            long lastId = 0;
            if (isInsert) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        lastId = keys.getLong(1);
                    }
                } catch (SQLException ignored) {
                    // Not all statements support getGeneratedKeys
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("update-count", affected);
            result.put("last-insert-id", lastId);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static void setParameters(PreparedStatement ps, List params) throws SQLException {
        if (params == null || params.isEmpty()) return;
        for (Object p : params) {
            if (p instanceof Map) {
                Map<String, Object> param = (Map<String, Object>) p;
                int index = ((Number) param.get("index")).intValue();
                Object value = param.get("value");
                ps.setObject(index, value);
            }
        }
    }

    private static ClouderbySessionManager.SessionData getSessionOrThrow(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new RuntimeException("SESSION_NOT_FOUND:Session ID is required");
        }
        ClouderbySessionManager.SessionData session = sessionManager.getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("SESSION_NOT_FOUND:Session not found or expired");
        }
        return session;
    }

    private static String getFirstKeyword(String sql) {
        if (sql == null) return "";
        return sql.trim().split("\\s+")[0].toUpperCase();
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') count++;
        }
        return count;
    }
}
