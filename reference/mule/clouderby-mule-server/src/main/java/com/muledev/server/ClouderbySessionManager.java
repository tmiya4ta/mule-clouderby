package com.muledev.server;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages clouderby sessions with dedicated Derby connections per session.
 * Each session has its own JDBC Connection for transaction isolation.
 *
 * Uses a DataSource (EmbeddedDataSource) injected via Spring.
 * Derby classloader fix: captures the DataSource's classloader at construction time
 * and sets thread context classloader before every getConnection() call to avoid XJ040.C.
 */
public class ClouderbySessionManager {
    private final DataSource dataSource;
    private final ClassLoader derbyClassLoader;
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public ClouderbySessionManager(DataSource dataSource) {
        this.dataSource = dataSource;
        // Capture the classloader that loaded the EmbeddedDataSource class.
        // This is the shared library classloader where Derby resides.
        this.derbyClassLoader = dataSource.getClass().getClassLoader();
    }

    /**
     * Get a connection from the DataSource, ensuring the thread context classloader
     * matches the one that Derby was booted with (the shared lib classloader).
     */
    private Connection getConnectionWithClassLoader() throws SQLException {
        Thread t = Thread.currentThread();
        ClassLoader original = t.getContextClassLoader();
        try {
            t.setContextClassLoader(derbyClassLoader);
            return dataSource.getConnection();
        } finally {
            t.setContextClassLoader(original);
        }
    }

    public static class CursorData {
        public final ResultSet resultSet;
        public final PreparedStatement preparedStatement;
        public final java.util.List<Map<String, Object>> columns;
        public final int fetchSize;
        /** Row that was read ahead to detect hasMore, must be returned first on next fetch */
        public java.util.List<Object> pendingRow;

        public CursorData(ResultSet rs, PreparedStatement ps,
                          java.util.List<Map<String, Object>> columns, int fetchSize) {
            this.resultSet = rs;
            this.preparedStatement = ps;
            this.columns = columns;
            this.fetchSize = fetchSize;
        }

        public void close() {
            try { resultSet.close(); } catch (SQLException ignored) {}
            try { preparedStatement.close(); } catch (SQLException ignored) {}
        }
    }

    public static class SessionData {
        private final String sessionId;
        private final Connection connection;
        private final Map<String, String> statements = new ConcurrentHashMap<>();
        private final Map<String, CursorData> cursors = new ConcurrentHashMap<>();
        private final AtomicInteger stmtCounter = new AtomicInteger(0);
        private final AtomicInteger cursorCounter = new AtomicInteger(0);
        private boolean inTransaction = false;

        public SessionData(String sessionId, Connection connection) {
            this.sessionId = sessionId;
            this.connection = connection;
        }

        public String getSessionId() { return sessionId; }
        public Connection getConnection() { return connection; }
        public boolean isInTransaction() { return inTransaction; }

        // --- Statement management (SQL string based) ---

        public String addStatement(String sql) {
            String id = "stmt-" + stmtCounter.incrementAndGet();
            statements.put(id, sql);
            return id;
        }

        public String getStatementSql(String id) {
            return statements.get(id);
        }

        public void removeStatement(String id) {
            statements.remove(id);
        }

        // --- Cursor management ---

        public String addCursor(CursorData cursor) {
            String id = "cur-" + cursorCounter.incrementAndGet();
            cursors.put(id, cursor);
            return id;
        }

        public CursorData getCursor(String id) {
            return cursors.get(id);
        }

        public void removeCursor(String id) {
            CursorData cursor = cursors.remove(id);
            if (cursor != null) {
                cursor.close();
            }
        }

        // --- Transaction management ---

        public void beginTransaction() throws SQLException {
            if (inTransaction) {
                throw new SQLException("TX_ALREADY_ACTIVE:Transaction already in progress");
            }
            connection.setAutoCommit(false);
            inTransaction = true;
        }

        public void commitTransaction() throws SQLException {
            if (!inTransaction) {
                throw new SQLException("TX_NOT_ACTIVE:No active transaction to commit");
            }
            connection.commit();
            connection.setAutoCommit(true);
            inTransaction = false;
        }

        public void rollbackTransaction() throws SQLException {
            if (!inTransaction) {
                throw new SQLException("TX_NOT_ACTIVE:No active transaction to rollback");
            }
            connection.rollback();
            connection.setAutoCommit(true);
            inTransaction = false;
        }

        public void close() {
            for (CursorData cursor : cursors.values()) {
                cursor.close();
            }
            cursors.clear();
            statements.clear();
            if (inTransaction) {
                try { connection.rollback(); } catch (SQLException ignored) {}
            }
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    public SessionData createSession(String database) throws SQLException {
        Connection conn = getConnectionWithClassLoader();
        String sessionId = UUID.randomUUID().toString();
        SessionData session = new SessionData(sessionId, conn);
        sessions.put(sessionId, session);
        return session;
    }

    public SessionData getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean closeSession(String sessionId) {
        SessionData session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            return true;
        }
        return false;
    }

    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Get a connection from the DataSource for system-level queries
     * (metadata endpoints, UI endpoints that don't require a session).
     */
    public Connection getSystemConnection() throws SQLException {
        return getConnectionWithClassLoader();
    }
}
