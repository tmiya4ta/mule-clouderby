package com.muledev.init;

import com.muledev.server.ClouderbySessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dataset profiles: a named (schema + seed data) bundle that can be swapped at
 * runtime. Each profile lives under {@code /init/profiles/<id>/} in the jar:
 *
 * <pre>
 *   profile.json          UI metadata (served verbatim; see below)
 *   ddl.sql               CREATE TABLE / CREATE INDEX statements
 *   identity_restart.sql  ALTER ... RESTART WITH for IDENTITY columns
 *   csv/&lt;TABLE&gt;.csv       one file per table, header = column names
 * </pre>
 *
 * profile.json is handed to the UI as raw text and parsed there with DataWeave,
 * so no JSON library is needed on the app classloader. The only field this class
 * needs in Java is {@code tables}, which it extracts with a regex -- these files
 * ship with the jar and are not user input.
 */
public class ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManager.class);

    /** Bookkeeping table. Never dropped by a profile switch. */
    private static final String STATE_TABLE = "CLOUDERBY_PROFILE";

    private static DataSource dataSource;
    private static ClouderbySessionManager sessionManager;
    private static volatile String currentProfileId;

    public static void configure(DataSource ds, ClouderbySessionManager sm) {
        dataSource = ds;
        sessionManager = sm;
    }

    // ================================================================ catalog

    /** Profile ids in catalog order, from /init/profiles.json. */
    public static List<String> profileIds() {
        String json = readResource("/init/profiles.json");
        if (json == null) return Collections.emptyList();
        return stringArray(json, "profiles");
    }

    public static String defaultProfileId() {
        String json = readResource("/init/profiles.json");
        if (json == null) return null;
        Matcher m = Pattern.compile("\"defaultProfile\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Table names of a profile, in FK-safe load order. */
    public static List<String> tablesOf(String profileId) {
        String json = readResource("/init/profiles/" + profileId + "/profile.json");
        if (json == null) throw new IllegalArgumentException("unknown profile: " + profileId);
        return stringArray(json, "tables");
    }

    /** Raw profile.json text, for the UI to parse. */
    public static String profileJson(String profileId) {
        String json = readResource("/init/profiles/" + profileId + "/profile.json");
        if (json == null) throw new IllegalArgumentException("unknown profile: " + profileId);
        return json;
    }

    /**
     * Everything the UI needs to render the profile picker: the catalog, which
     * profile is loaded, and the live row count per table of the current one.
     */
    public static Map<String, Object> status() throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (String id : profileIds()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", id);
            p.put("json", profileJson(id));
            p.put("tableCount", tablesOf(id).size());
            profiles.add(p);
        }
        out.put("profiles", profiles);
        out.put("current", currentProfile());
        out.put("defaultProfile", defaultProfileId());
        out.put("openSessions", sessionManager == null ? 0 : sessionManager.getSessionCount());

        List<Map<String, Object>> counts = new ArrayList<>();
        String cur = currentProfileId;
        if (cur != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                for (String t : tablesOf(cur)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("table", t);
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                        row.put("rows", rs.next() ? rs.getInt(1) : 0);
                    } catch (Exception e) {
                        row.put("rows", -1);
                    }
                    counts.add(row);
                }
            }
        }
        out.put("tables", counts);
        return out;
    }

    // ================================================================ apply

    /**
     * Drop every application table, then create and seed the given profile.
     *
     * <p>This changes the shape of the database, so every open clouderby session
     * is closed first -- their cached PreparedStatements would otherwise fail
     * against tables that no longer exist. Clients must create a new session.
     *
     * <p>Unlike the startup path this never swallows a failure: per-table
     * outcomes are returned and {@code ok} is false if anything went wrong.
     */
    public static Map<String, Object> apply(String profileId, String sessionId) throws Exception {
        // This drops every table, so it is gated on a valid clouderby session --
        // the same bar as any other write path. The check lives here, next to the
        // destructive work, rather than as a flow-ref that an XML edit could drop.
        if (sessionManager != null && sessionManager.getSession(nullToEmpty(sessionId)) == null) {
            Map<String, Object> denied = new LinkedHashMap<>();
            denied.put("ok", false);
            denied.put("status", 401);
            denied.put("error", "A valid X-Clouderby-Session-Id is required to apply a profile");
            return denied;
        }
        if (!profileIds().contains(profileId)) {
            Map<String, Object> unknown = new LinkedHashMap<>();
            unknown.put("ok", false);
            unknown.put("status", 400);
            unknown.put("error", "Unknown profile: " + profileId);
            unknown.put("available", profileIds());
            return unknown;
        }
        return applyInternal(profileId);
    }

    /**
     * The unauthenticated path, for startup seeding.
     *
     * <p>Package-private on purpose: {@code java:invoke-static} can only reach
     * public methods, so no Mule flow can call this and skip the session check.
     */
    static synchronized Map<String, Object> applyInternal(String profileId) throws Exception {
        List<String> tables = tablesOf(profileId);   // throws on unknown id

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profileId);
        result.put("status", 200);
        List<String> errors = new ArrayList<>();

        // The caller's own session goes too: after the swap its cached
        // PreparedStatements point at tables that no longer exist.
        int closed = sessionManager == null ? 0 : sessionManager.closeAllSessions();
        result.put("sessionsClosed", closed);

        // Derby's driver is loaded by the shared-library classloader; without this
        // a background/HTTP thread hits XJ040.C.
        Thread.currentThread().setContextClassLoader(dataSource.getClass().getClassLoader());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            result.put("dropped", dropAllTables(conn, stmt, errors));

            for (String sql : loadSql("/init/profiles/" + profileId + "/ddl.sql")) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    errors.add("ddl: " + firstLine(sql) + ": " + e.getMessage());
                }
            }

            List<Map<String, Object>> loaded = new ArrayList<>();
            int total = 0;
            for (String table : tables) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", table);
                try {
                    int n = loadCsv(conn, profileId, table);
                    row.put("rows", n);
                    total += n;
                } catch (Exception e) {
                    row.put("rows", 0);
                    row.put("error", e.getMessage());
                    errors.add(table + ": " + e.getMessage());
                }
                loaded.add(row);
            }
            result.put("tables", loaded);
            result.put("totalRows", total);

            for (String sql : loadSql("/init/profiles/" + profileId + "/identity_restart.sql")) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    errors.add("identity: " + firstLine(sql) + ": " + e.getMessage());
                }
            }

            rememberProfile(conn, stmt, profileId);
            currentProfileId = profileId;
        }

        result.put("errors", errors);
        result.put("ok", errors.isEmpty());
        LOG.info("[PROFILE] applied {} ({} errors)", profileId, errors.size());
        return result;
    }

    /**
     * Drop every table in the APP schema except {@link #STATE_TABLE}.
     *
     * <p>Order is not hardcoded: Derby refuses to drop a table that another
     * table's foreign key still references, so we simply retry the failures
     * until a pass makes no progress. That handles any FK graph without having
     * to model it per profile.
     */
    private static List<String> dropAllTables(Connection conn, Statement stmt, List<String> errors)
            throws Exception {
        List<String> remaining = userTables(conn);
        List<String> dropped = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<String> failed = new ArrayList<>();
            for (String t : remaining) {
                try {
                    stmt.execute("DROP TABLE " + t);
                    dropped.add(t);
                } catch (Exception e) {
                    failed.add(t);
                }
            }
            if (failed.size() == remaining.size()) {
                // No progress: a genuine cycle or a permission problem. Report the
                // real reason rather than looping forever.
                for (String t : failed) {
                    try {
                        stmt.execute("DROP TABLE " + t);
                    } catch (Exception e) {
                        errors.add("drop " + t + ": " + e.getMessage());
                    }
                }
                break;
            }
            remaining = failed;
        }
        return dropped;
    }

    private static List<String> userTables(Connection conn) throws Exception {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT T.TABLENAME FROM SYS.SYSTABLES T "
                   + "JOIN SYS.SYSSCHEMAS S ON T.SCHEMAID = S.SCHEMAID "
                   + "WHERE S.SCHEMANAME = 'APP' AND T.TABLETYPE = 'T'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String t = rs.getString(1);
                if (!STATE_TABLE.equalsIgnoreCase(t)) tables.add(t);
            }
        }
        return tables;
    }

    // ================================================================ state

    /** Which profile is loaded, read back from the DB on first call. */
    public static String currentProfile() {
        if (currentProfileId != null) return currentProfileId;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT PROFILE_ID FROM " + STATE_TABLE)) {
            if (rs.next()) currentProfileId = rs.getString(1);
        } catch (Exception ignored) {
            // table not created yet -- nothing applied so far
        }
        return currentProfileId;
    }

    private static void rememberProfile(Connection conn, Statement stmt, String profileId) {
        try {
            stmt.execute("CREATE TABLE " + STATE_TABLE + " ("
                       + "PROFILE_ID VARCHAR(100) NOT NULL PRIMARY KEY, "
                       + "APPLIED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (Exception ignored) {
            // already exists
        }
        try {
            stmt.executeUpdate("DELETE FROM " + STATE_TABLE);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + STATE_TABLE + " (PROFILE_ID) VALUES (?)")) {
                ps.setString(1, profileId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOG.warn("[PROFILE] could not record current profile: {}", e.getMessage());
        }
    }

    // ================================================================ loading

    static int loadCsv(Connection conn, String profileId, String tableName) throws Exception {
        String path = "/init/profiles/" + profileId + "/csv/" + tableName + ".csv";
        try (InputStream is = ProfileManager.class.getResourceAsStream(path)) {
            if (is == null) throw new Exception("CSV not found: " + path);

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null) throw new Exception("Empty CSV: " + path);
            String[] columns = parseCsvLine(headerLine);

            int[] colTypes = new int[columns.length];
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, tableName, null)) {
                Map<String, Integer> typeMap = new java.util.HashMap<>();
                while (rs.next()) {
                    typeMap.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getInt("DATA_TYPE"));
                }
                for (int i = 0; i < columns.length; i++) {
                    colTypes[i] = typeMap.getOrDefault(columns[i].toUpperCase(), java.sql.Types.VARCHAR);
                }
            }

            String sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") VALUES ("
                       + String.join(", ", Collections.nCopies(columns.length, "?")) + ")";

            int count = 0;
            String firstError = null;
            int failed = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] values = parseCsvLine(line);
                    try {
                        for (int i = 0; i < columns.length; i++) {
                            String val = (i < values.length) ? values[i] : "";
                            if (val.isEmpty()) ps.setNull(i + 1, colTypes[i]);
                            else setTypedValue(ps, i + 1, val, colTypes[i]);
                        }
                        ps.executeUpdate();
                        count++;
                    } catch (Exception e) {
                        failed++;
                        if (firstError == null) firstError = e.getMessage();
                    }
                }
            }
            if (failed > 0) {
                throw new Exception(failed + " row(s) rejected, first: " + firstError);
            }
            return count;
        }
    }

    static void setTypedValue(PreparedStatement ps, int idx, String val, int sqlType) throws Exception {
        switch (sqlType) {
            case java.sql.Types.INTEGER:
            case java.sql.Types.SMALLINT:
            case java.sql.Types.TINYINT:
                ps.setInt(idx, Integer.parseInt(val));
                break;
            case java.sql.Types.BIGINT:
                ps.setLong(idx, Long.parseLong(val));
                break;
            case java.sql.Types.DOUBLE:
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
                ps.setDouble(idx, Double.parseDouble(val));
                break;
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
                ps.setBigDecimal(idx, new java.math.BigDecimal(val));
                break;
            case java.sql.Types.TIMESTAMP:
                ps.setTimestamp(idx, java.sql.Timestamp.valueOf(val));
                break;
            case java.sql.Types.DATE:
                ps.setDate(idx, java.sql.Date.valueOf(val));
                break;
            default:
                ps.setString(idx, val);
                break;
        }
    }

    /** RFC 4180 CSV line parser with double-quote support. */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int len = line.length();

        while (i < len) {
            if (line.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < len) {
                    if (line.charAt(i) == '"') {
                        if (i + 1 < len && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        sb.append(line.charAt(i));
                        i++;
                    }
                }
                fields.add(sb.toString());
                if (i < len && line.charAt(i) == ',') i++;
            } else {
                int start = i;
                while (i < len && line.charAt(i) != ',') i++;
                fields.add(line.substring(start, i));
                if (i < len) i++;
            }
        }
        return fields.toArray(new String[0]);
    }

    static List<String> loadSql(String path) throws Exception {
        try (InputStream is = ProfileManager.class.getResourceAsStream(path)) {
            if (is == null) return Collections.emptyList();
            String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
            List<String> stmts = new ArrayList<>();
            for (String s : content.split(";")) {
                if (s.trim().length() > 5) stmts.add(s.trim());
            }
            return stmts;
        }
    }

    // ================================================================ helpers

    private static String readResource(String path) {
        try (InputStream is = ProfileManager.class.getResourceAsStream(path)) {
            if (is == null) return null;
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Pull a flat array of strings out of a JSON document we ship ourselves. */
    private static List<String> stringArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!m.find()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        Matcher item = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (item.find()) out.add(item.group(1));
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstLine(String sql) {
        int nl = sql.indexOf('\n');
        String s = nl < 0 ? sql : sql.substring(0, nl);
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
