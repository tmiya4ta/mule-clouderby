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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Dataset profiles: a named (schema + seed data) bundle.
 *
 * <p>Every profile lives in its own Derby schema and they are <em>all</em> loaded
 * at startup, so switching is just {@code SET SCHEMA} on a connection -- no data
 * is dropped and both datasets stay queryable at any time. A profile id maps to
 * a schema by upper-casing it: {@code finance} to {@code FINANCE}.
 *
 * <p>Each profile ships under {@code /init/profiles/<id>/} in the jar:
 *
 * <pre>
 *   profile.json          UI metadata (served verbatim; see below)
 *   ddl.sql               CREATE TABLE / CREATE INDEX, unqualified
 *   identity_restart.sql  ALTER ... RESTART WITH for IDENTITY columns
 *   csv/&lt;TABLE&gt;.csv       one file per table, header = column names
 * </pre>
 *
 * The SQL is unqualified on purpose: it is run on a connection whose schema is
 * already set, so the same file seeds whichever schema the profile owns.
 *
 * <p>profile.json is handed to the UI as raw text and parsed there with DataWeave,
 * so no JSON library is needed on the app classloader. The only field this class
 * needs in Java is {@code tables}, which it extracts with a regex -- these files
 * ship with the jar and are not user input.
 */
public class ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManager.class);

    /** Bookkeeping table, in APP so it is never inside a profile's schema. */
    private static final String STATE_TABLE = "APP.CLOUDERBY_STATE";

    private static DataSource dataSource;
    private static ClouderbySessionManager sessionManager;

    /** Schema handed to a new session that does not ask for one. */
    private static volatile String defaultProfileId;

    private static volatile String startupSource;

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

    public static String catalogDefaultProfileId() {
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

    /** The Derby schema a profile owns. */
    public static String schemaOf(String profileId) {
        return profileId == null ? null : profileId.toUpperCase();
    }

    /** The profile a schema belongs to, or null if the schema is not a profile's. */
    public static String profileOfSchema(String schema) {
        if (schema == null) return null;
        for (String id : profileIds()) {
            if (schemaOf(id).equalsIgnoreCase(schema)) return id;
        }
        return null;
    }

    /**
     * Resolve whatever a client asked for -- a profile id, a schema name, or
     * nothing -- to a profile id. Falls back to the server default.
     */
    public static String resolveProfile(String requested) {
        if (requested != null && !requested.trim().isEmpty()) {
            String r = requested.trim();
            for (String id : profileIds()) {
                if (id.equalsIgnoreCase(r) || schemaOf(id).equalsIgnoreCase(r)) return id;
            }
        }
        return defaultProfile();
    }

    // ================================================================ status

    /**
     * Everything the UI needs: the catalog with live row counts for every
     * profile (they all exist at once), the server default, and the schema the
     * calling session is currently on.
     */
    public static Map<String, Object> status(String sessionId) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();

        List<Map<String, Object>> profiles = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            for (String id : profileIds()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", id);
                p.put("schema", schemaOf(id));
                p.put("json", profileJson(id));
                List<String> tables = tablesOf(id);
                p.put("tableCount", tables.size());

                List<Map<String, Object>> counts = new ArrayList<>();
                int total = 0;
                try (Statement st = conn.createStatement()) {
                    for (String t : tables) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("table", t);
                        try (ResultSet rs = st.executeQuery(
                                "SELECT COUNT(*) FROM " + schemaOf(id) + "." + t)) {
                            int n = rs.next() ? rs.getInt(1) : 0;
                            row.put("rows", n);
                            total += n;
                        } catch (Exception e) {
                            row.put("rows", -1);
                        }
                        counts.add(row);
                    }
                }
                p.put("tables", counts);
                p.put("totalRows", total);
                profiles.add(p);
            }
        }
        out.put("profiles", profiles);
        out.put("defaultProfile", defaultProfile());
        out.put("catalogDefault", catalogDefaultProfileId());
        out.put("openSessions", sessionManager == null ? 0 : sessionManager.getSessionCount());

        Map<String, Object> startup = new LinkedHashMap<>();
        startup.put("profile", defaultProfileId);
        startup.put("source", startupSource);
        out.put("startup", startup);

        // Which schema is the caller looking at right now?
        String currentSchema = sessionSchema(sessionId);
        out.put("currentSchema", currentSchema);
        out.put("current", profileOfSchema(currentSchema));
        return out;
    }

    private static String sessionSchema(String sessionId) {
        if (sessionManager == null || sessionId == null) return null;
        ClouderbySessionManager.SessionData s = sessionManager.getSession(sessionId);
        if (s == null) return null;
        try {
            return s.getConnection().getSchema();
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================ select

    /**
     * Switch which dataset is in view. Non-destructive: nothing is dropped and
     * the other profiles stay queryable (schema-qualified) throughout.
     *
     * <p>Sets the schema on the caller's own session, and makes it the default
     * that new sessions get. Sessions already open are left alone -- changing
     * the schema under a running query would be worse than leaving them.
     */
    public static Map<String, Object> select(String requested, String sessionId) throws Exception {
        Map<String, Object> denied = requireSession(sessionId);
        if (denied != null) return denied;

        String profileId = matchProfile(requested);
        if (profileId == null) return unknownProfile(requested);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", 200);
        out.put("profile", profileId);
        out.put("schema", schemaOf(profileId));

        if (sessionManager != null && sessionId != null) {
            ClouderbySessionManager.SessionData s = sessionManager.getSession(sessionId);
            if (s != null) {
                setSchema(s.getConnection(), schemaOf(profileId));
                out.put("sessionSwitched", true);
            }
        }

        setDefaultProfile(profileId);
        out.put("defaultProfile", profileId);
        out.put("otherSessionsAffected", false);
        out.put("ok", true);
        LOG.info("[PROFILE] selected {} (schema {})", profileId, schemaOf(profileId));
        return out;
    }

    // ================================================================ reload

    /**
     * Rebuild one profile's schema from the jar: drop its tables, re-run the DDL
     * and reload the CSVs. Destructive, but scoped to that one schema -- the
     * other profiles are untouched.
     *
     * <p>Unlike the startup path this never swallows a failure: per-table
     * outcomes are returned and {@code ok} is false if anything went wrong.
     */
    public static Map<String, Object> reload(String requested, String sessionId) throws Exception {
        Map<String, Object> denied = requireSession(sessionId);
        if (denied != null) return denied;

        String profileId = matchProfile(requested);
        if (profileId == null) return unknownProfile(requested);

        Map<String, Object> result = seed(profileId, true);
        result.put("status", Boolean.TRUE.equals(result.get("ok")) ? 200 : 500);
        return result;
    }

    // ================================================================ seeding

    /**
     * Create and populate every profile's schema that is not already populated.
     * Existing data is left alone, so a restart against a persistent Derby
     * directory does not wipe anything.
     */
    static Map<String, Object> ensureAllSeeded() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> per = new ArrayList<>();
        boolean ok = true;
        for (String id : profileIds()) {
            try {
                if (isPopulated(id)) {
                    Map<String, Object> skipped = new LinkedHashMap<>();
                    skipped.put("profile", id);
                    skipped.put("skipped", "already populated");
                    per.add(skipped);
                    LOG.info("[DB-INIT] {} already populated; leaving as is", schemaOf(id));
                    continue;
                }
                Map<String, Object> r = seed(id, false);
                per.add(r);
                ok &= Boolean.TRUE.equals(r.get("ok"));
                LOG.info("[DB-INIT] {} seeded: {} rows, ok={}",
                         schemaOf(id), r.get("totalRows"), r.get("ok"));
            } catch (Exception e) {
                ok = false;
                LOG.error("[DB-INIT] failed to seed {}: {}", id, e.getMessage(), e);
            }
        }
        out.put("profiles", per);
        out.put("ok", ok);
        return out;
    }

    private static boolean isPopulated(String profileId) {
        String schema = schemaOf(profileId);
        try (Connection conn = dataSource.getConnection()) {
            return !tablesIn(conn, schema).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Create the schema if needed, then (re)build its tables and data. */
    private static synchronized Map<String, Object> seed(String profileId, boolean dropFirst)
            throws Exception {
        List<String> tables = tablesOf(profileId);   // throws on unknown id
        String schema = schemaOf(profileId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profileId);
        result.put("schema", schema);
        List<String> errors = new ArrayList<>();

        // Derby's driver comes from the shared-library classloader; without this
        // a background/HTTP thread hits XJ040.C.
        Thread.currentThread().setContextClassLoader(dataSource.getClass().getClassLoader());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try {
                stmt.execute("CREATE SCHEMA " + schema);
            } catch (SQLException e) {
                // X0Y68: already exists -- the normal case on a reload
            }
            setSchema(conn, schema);

            if (dropFirst) {
                result.put("dropped", dropTablesIn(conn, stmt, schema, errors));
            }

            // The DDL and identity SQL are unqualified, so they land in the
            // schema we just set on this connection.
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
                    int n = loadCsv(conn, profileId, schema, table);
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
        }

        result.put("errors", errors);
        result.put("ok", errors.isEmpty());
        return result;
    }

    /**
     * Drop every table in one schema.
     *
     * <p>Order is not hardcoded: Derby refuses to drop a table that another
     * table's foreign key still references, so we simply retry the failures
     * until a pass makes no progress. That handles any FK graph without having
     * to model it per profile.
     */
    private static List<String> dropTablesIn(Connection conn, Statement stmt, String schema,
                                             List<String> errors) throws Exception {
        List<String> remaining = tablesIn(conn, schema);
        List<String> dropped = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<String> failed = new ArrayList<>();
            for (String t : remaining) {
                try {
                    stmt.execute("DROP TABLE " + schema + "." + t);
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
                        stmt.execute("DROP TABLE " + schema + "." + t);
                    } catch (Exception e) {
                        errors.add("drop " + schema + "." + t + ": " + e.getMessage());
                    }
                }
                break;
            }
            remaining = failed;
        }
        return dropped;
    }

    static List<String> tablesIn(Connection conn, String schema) throws Exception {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT T.TABLENAME FROM SYS.SYSTABLES T "
                   + "JOIN SYS.SYSSCHEMAS S ON T.SCHEMAID = S.SCHEMAID "
                   + "WHERE S.SCHEMANAME = ? AND T.TABLETYPE = 'T'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    /** Derby has no parameterised SET SCHEMA, and the name is ours, not input. */
    public static void setSchema(Connection conn, String schema) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("SET SCHEMA " + schema);
        }
    }

    // ================================================================ state

    /** Schema a new session lands on when it does not ask for one. */
    public static String defaultProfile() {
        if (defaultProfileId != null) return defaultProfileId;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT PROFILE_ID FROM " + STATE_TABLE)) {
            if (rs.next()) defaultProfileId = rs.getString(1);
        } catch (Exception ignored) {
            // table not created yet
        }
        return defaultProfileId;
    }

    static void setDefaultProfile(String profileId) {
        defaultProfileId = profileId;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("CREATE TABLE " + STATE_TABLE + " ("
                           + "PROFILE_ID VARCHAR(100) NOT NULL PRIMARY KEY, "
                           + "APPLIED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            } catch (Exception ignored) {
                // already exists
            }
            stmt.executeUpdate("DELETE FROM " + STATE_TABLE);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + STATE_TABLE + " (PROFILE_ID) VALUES (?)")) {
                ps.setString(1, profileId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOG.warn("[PROFILE] could not record the default profile: {}", e.getMessage());
        }
    }

    /**
     * Recorded by {@link DatabaseInitializer} so operators can see where the
     * default came from. The log line alone is not enough: CloudHub keeps only
     * the first few lines of startup output.
     */
    static void recordStartupResolution(String profile, String source) {
        defaultProfileId = profile;
        startupSource = source;
    }

    // ================================================================ guards

    private static Map<String, Object> requireSession(String sessionId) {
        if (sessionManager == null) return null;
        if (sessionManager.getSession(sessionId == null ? "" : sessionId) != null) return null;
        Map<String, Object> denied = new LinkedHashMap<>();
        denied.put("ok", false);
        denied.put("status", 401);
        denied.put("error", "A valid X-Clouderby-Session-Id is required");
        return denied;
    }

    private static String matchProfile(String requested) {
        if (requested == null) return null;
        String r = requested.trim();
        for (String id : profileIds()) {
            if (id.equalsIgnoreCase(r) || schemaOf(id).equalsIgnoreCase(r)) return id;
        }
        return null;
    }

    private static Map<String, Object> unknownProfile(String requested) {
        Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("ok", false);
        unknown.put("status", 400);
        unknown.put("error", "Unknown profile: " + requested);
        unknown.put("available", profileIds());
        return unknown;
    }

    // ================================================================ loading

    static int loadCsv(Connection conn, String profileId, String schema, String tableName)
            throws Exception {
        String path = "/init/profiles/" + profileId + "/csv/" + tableName + ".csv";
        try (InputStream is = ProfileManager.class.getResourceAsStream(path)) {
            if (is == null) throw new Exception("CSV not found: " + path);

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String headerLine = reader.readLine();
            if (headerLine == null) throw new Exception("Empty CSV: " + path);
            String[] columns = parseCsvLine(headerLine);

            // Qualified by schema: the same table name exists in more than one
            // profile's schema in principle, so an unqualified lookup is ambiguous.
            int[] colTypes = new int[columns.length];
            try (ResultSet rs = conn.getMetaData().getColumns(null, schema, tableName, null)) {
                Map<String, Integer> typeMap = new java.util.HashMap<>();
                while (rs.next()) {
                    typeMap.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getInt("DATA_TYPE"));
                }
                for (int i = 0; i < columns.length; i++) {
                    colTypes[i] = typeMap.getOrDefault(columns[i].toUpperCase(), java.sql.Types.VARCHAR);
                }
            }

            String sql = "INSERT INTO " + schema + "." + tableName
                       + " (" + String.join(", ", columns) + ") VALUES ("
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

    private static String firstLine(String sql) {
        int nl = sql.indexOf('\n');
        String s = nl < 0 ? sql : sql.substring(0, nl);
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
