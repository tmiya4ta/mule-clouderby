package com.muledev.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DatabaseInitializer implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DataSource dataSource;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        new Thread(this::init, "db-init").start();
        LOG.info("[DB-INIT] Scheduled in background thread");
    }

    /** CSV tables to load in FK-safe order (master -> transaction -> detail). */
    private static final String[] CSV_TABLES = {
        // MDM (master data)
        "MDM_CUSTOMERS",
        "MDM_PRODUCTS",
        "MDM_ORGANIZATIONS",
        "MDM_SUPPLIERS",
        "MDM_MATERIALS",
        // INV (inventory master)
        "INV_WAREHOUSES",
        "INV_INVENTORY",
        // SAL (sales)
        "SAL_SALES_ORDERS",
        "SAL_SALES_ORDER_ITEMS",
        "SAL_QUOTATIONS",
        "SAL_QUOTATION_ITEMS",
        "SAL_CONTRACTS",
        "SAL_PRICE_AGREEMENTS",
        // EQP (equipment)
        "EQP_EQUIPMENT",
        "EQP_FAILURES",
        "EQP_MAINTENANCE_PLANS",
        "EQP_OPERATION_LOGS",
        // FIN (finance)
        "FIN_RECEIVABLES",
        "FIN_AR_PAYMENTS",
        "FIN_PAYABLES",
        "FIN_BUDGETS",
        "FIN_BUDGET_PERIODS",
        "FIN_PRODUCT_COSTS",
        // MFG (manufacturing)
        "MFG_RECIPES",
        "MFG_RECIPE_PARAMETERS",
        "MFG_PRODUCTION_PLANS",
        "MFG_PRODUCTION_ORDERS",
        "MFG_WORK_ORDERS",
        "MFG_PRODUCTION_RESULTS",
        "MFG_LOTS",
        "MFG_LOT_HISTORY",
        "MFG_PROCESS_RECORDS",
        "MFG_INSPECTIONS",
        "MFG_QUALITY_INSPECTIONS",
        // PRC (procurement)
        "PRC_PURCHASE_REQUESTS",
        "PRC_PURCHASE_REQUEST_ITEMS",
        "PRC_PURCHASE_ORDERS",
        "PRC_PURCHASE_ORDER_ITEMS",
        "PRC_GOODS_RECEIPTS",
        "PRC_GOODS_RECEIPT_ITEMS",
        // INV (transactions)
        "INV_RECEIPTS",
        "INV_RECEIPT_ITEMS",
        "INV_SHIPMENTS",
        "INV_SHIPMENT_ITEMS",
        "INV_STOCK_MOVEMENTS",
        // RND (R&D)
        "RND_PROJECTS",
        "RND_MILESTONES",
        "RND_DOCUMENTS",
        "RND_DOCUMENT_REVISIONS",
        "RND_PATENTS",
        "RND_PROTOTYPES",
        "RND_PROTOTYPE_EVALUATIONS",
    };

    private void init() {
        try { Thread.sleep(5000); } catch (InterruptedException e) { return; }

        LOG.info("[DB-INIT] Starting with Derby embedded connection...");

        // Set thread context classloader to the DataSource's classloader (shared lib)
        // to avoid Derby XJ040.C classloader conflict in Mule
        ClassLoader derbyClassLoader = dataSource.getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(derbyClassLoader);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            LOG.info("[DB-INIT] Connected to Derby via DataSource");

            // 1. DDL
            try {
                List<String> stmts = loadSql("/init/01_ddl.sql");
                for (String sql : stmts) {
                    stmt.execute(sql);
                }
                LOG.info("[DB-INIT] 01_ddl.sql done ({} statements)", stmts.size());
            } catch (Exception e) {
                LOG.warn("[DB-INIT] Skipping 01_ddl.sql: {}", e.getMessage());
            }

            // 2. Delete existing data in reverse FK order before reload
            for (int i = CSV_TABLES.length - 1; i >= 0; i--) {
                try {
                    int deleted = stmt.executeUpdate("DELETE FROM " + CSV_TABLES[i]);
                    if (deleted > 0) {
                        LOG.info("[DB-INIT] {} cleared ({} rows deleted)", CSV_TABLES[i], deleted);
                    }
                } catch (Exception e) {
                    LOG.warn("[DB-INIT] Could not clear {}: {}", CSV_TABLES[i], e.getMessage());
                }
            }

            // 3. CSV data load
            int totalRows = 0;
            for (String table : CSV_TABLES) {
                try {
                    int rows = loadCsv(conn, table);
                    totalRows += rows;
                    LOG.info("[DB-INIT] {} loaded ({} rows)", table, rows);
                } catch (Exception e) {
                    LOG.warn("[DB-INIT] Skipping {}: {}", table, e.getMessage());
                }
            }

            // 4. Identity counter restart
            try {
                List<String> stmts = loadSql("/init/99_identity_restart.sql");
                for (String sql : stmts) {
                    stmt.execute(sql);
                }
                LOG.info("[DB-INIT] 99_identity_restart.sql done ({} statements)", stmts.size());
            } catch (Exception e) {
                LOG.warn("[DB-INIT] Skipping 99_identity_restart.sql: {}", e.getMessage());
            }

            LOG.info("[DB-INIT] Completed successfully ({} total rows loaded)", totalRows);

        } catch (Exception e) {
            LOG.error("[DB-INIT] Error: {}", e.getMessage(), e);
        }
    }

    private int loadCsv(Connection conn, String tableName) throws Exception {
        String path = "/init/csv/" + tableName + ".csv";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new Exception("CSV not found: " + path);

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            // Header line -> column names
            String headerLine = reader.readLine();
            if (headerLine == null) throw new Exception("Empty CSV: " + path);
            String[] columns = parseCsvLine(headerLine);
            String colList = String.join(", ", columns);

            // Get column type info from DB metadata
            int[] colTypes = new int[columns.length];
            try (var rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
                java.util.Map<String, Integer> typeMap = new java.util.HashMap<>();
                while (rs.next()) {
                    typeMap.put(rs.getString("COLUMN_NAME").toUpperCase(), rs.getInt("DATA_TYPE"));
                }
                for (int i = 0; i < columns.length; i++) {
                    colTypes[i] = typeMap.getOrDefault(columns[i].toUpperCase(), java.sql.Types.VARCHAR);
                }
            }

            // Build INSERT with positional params
            String placeholders = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
            String sql = "INSERT INTO " + tableName + " (" + colList + ") VALUES (" + placeholders + ")";

            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseCsvLine(line);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < columns.length; i++) {
                        String val = (i < values.length) ? values[i] : "";
                        if (val.isEmpty()) {
                            ps.setNull(i + 1, colTypes[i]);
                        } else {
                            setTypedValue(ps, i + 1, val, colTypes[i]);
                        }
                    }
                    ps.executeUpdate();
                    count++;
                } catch (Exception e) {
                    LOG.warn("[DB-INIT] {}: row {} failed: {}", tableName, count + 1, e.getMessage());
                }
            }
            return count;
        }
    }

    private void setTypedValue(PreparedStatement ps, int idx, String val, int sqlType) throws Exception {
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
                // Quoted field
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < len) {
                    if (line.charAt(i) == '"') {
                        if (i + 1 < len && line.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                        } else {
                            i++; // skip closing quote
                            break;
                        }
                    } else {
                        sb.append(line.charAt(i));
                        i++;
                    }
                }
                fields.add(sb.toString());
                // Skip comma
                if (i < len && line.charAt(i) == ',') i++;
            } else {
                // Unquoted field
                int start = i;
                while (i < len && line.charAt(i) != ',') i++;
                fields.add(line.substring(start, i));
                if (i < len) i++; // skip comma
            }
        }
        return fields.toArray(new String[0]);
    }

    private List<String> loadSql(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                .lines().filter(l -> !l.trim().startsWith("--")).collect(Collectors.joining("\n"));
            List<String> stmts = new ArrayList<>();
            for (String s : content.split(";")) {
                if (s.trim().length() > 5) stmts.add(s.trim());
            }
            return stmts;
        }
    }
}
