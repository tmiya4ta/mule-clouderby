package io.gitlab.myst3m.clouderby.jdbc.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Benchmark against deployed CloudHub clouderby server.
 *
 * Run with:
 *   BENCH=true mvn test -Dtest=CloudHubBenchmark
 *
 * Override the target URL with -Dclouderby.bench.url=jdbc:clouderby://host:port/db?secure=true
 */
@DisplayName("CloudHub Benchmark")
@EnabledIfEnvironmentVariable(named = "BENCH", matches = "true")
class CloudHubBenchmark {

    private static final String DEFAULT_URL =
        "jdbc:clouderby://mule-clouderby-2m6ahj.pnwfdv.jpn-e1.cloudhub.io:443/testdb?secure=true";
    private static final String DEFAULT_USER = "mule";
    private static final String DEFAULT_PASSWORD = "mule123";

    private static String url() {
        return System.getProperty("clouderby.bench.url", DEFAULT_URL);
    }

    private static Connection connect() throws SQLException {
        String u = System.getProperty("clouderby.bench.user", DEFAULT_USER);
        String p = System.getProperty("clouderby.bench.password", DEFAULT_PASSWORD);
        return DriverManager.getConnection(url(), u, p);
    }

    @Test
    @DisplayName("Run all benchmark scenarios")
    void runAll() throws SQLException {
        System.out.println();
        System.out.println("=== CloudHub Benchmark ===");
        System.out.println("URL: " + url());
        System.out.println();

        try (Connection setup = connect()) {
            try (Statement s = setup.createStatement()) {
                tryDrop(s, "bench_t");
                tryDrop(s, "bench_ins");
                s.execute("CREATE TABLE bench_t (id INTEGER PRIMARY KEY, name VARCHAR(64), value DOUBLE)");
            }
            try (PreparedStatement ps = setup.prepareStatement(
                    "INSERT INTO bench_t (id, name, value) VALUES (?, ?, ?)")) {
                for (int i = 1; i <= 1000; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "row-" + i);
                    ps.setDouble(3, i * 1.5);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            System.out.println("Setup: bench_t with 1000 rows ready");
            System.out.println();
        }

        List<Result> results = new ArrayList<>();

        results.add(measure("S1  session open+close",            10, CloudHubBenchmark::sessionOpenClose));
        results.add(measureOnConn("S2  SELECT 1 (warm RTT)",     50, CloudHubBenchmark::selectOne));
        results.add(measureOnConn("S3a SELECT 1000 fetch=100",   10, c -> selectAll(c, 100)));
        results.add(measureOnConn("S3b SELECT 1000 fetch=1000",  10, c -> selectAll(c, 1000)));
        results.add(measureOnConn("S3c SELECT 1000 fetch=2000",  10, c -> selectAll(c, 2000)));
        results.add(measureOnConn("S4a INSERT 100 loop",          5, c -> insertLoop(c, 100)));
        results.add(measureOnConn("S4b INSERT 100 batch",         5, c -> insertBatch(c, 100)));
        results.add(measureOnConn("S5  getColumns metadata",     20, CloudHubBenchmark::getColumns));
        results.add(measureOnConn("S6a prepare each call x10",   10, c -> prepareEach(c, 10)));
        results.add(measureOnConn("S6b prepare once + 10 exec",  10, c -> prepareReuse(c, 10)));
        results.add(measureOnConn("S7  SELECT 1000 fetch=100 +work",  5, c -> selectWithWork(c, 100)));

        try (Connection cleanup = connect()) {
            try (Statement s = cleanup.createStatement()) {
                tryDrop(s, "bench_t");
                tryDrop(s, "bench_ins");
            }
        }

        System.out.println();
        printResults(results);
    }

    private static void tryDrop(Statement s, String table) {
        try {
            s.execute("DROP TABLE " + table);
        } catch (SQLException ignored) {
            // table doesn't exist — Derby has no IF EXISTS pre-10.5
        }
    }

    // ===== scenarios =====

    private static void sessionOpenClose() throws SQLException {
        try (Connection c = connect()) {
            // open+close, no query
        }
    }

    private static void selectOne(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("VALUES 1")) {
            rs.next();
            rs.getInt(1);
        }
    }

    private static void selectAll(Connection c, int fetchSize) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.setFetchSize(fetchSize);
            try (ResultSet rs = s.executeQuery("SELECT id, name, value FROM bench_t ORDER BY id")) {
                int n = 0;
                while (rs.next()) {
                    rs.getInt(1);
                    rs.getString(2);
                    rs.getDouble(3);
                    n++;
                }
                if (n != 1000) throw new SQLException("expected 1000 rows, got " + n);
            }
        }
    }

    private static void ensureBenchIns(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            try {
                s.execute("CREATE TABLE bench_ins (id INTEGER, v VARCHAR(64))");
            } catch (SQLException ignored) {
                // already exists
            }
            s.execute("DELETE FROM bench_ins");
        }
    }

    private static void insertLoop(Connection c, int n) throws SQLException {
        ensureBenchIns(c);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bench_ins (id, v) VALUES (?, ?)")) {
            for (int i = 0; i < n; i++) {
                ps.setInt(1, i);
                ps.setString(2, "v-" + i);
                ps.executeUpdate();
            }
        }
    }

    private static void insertBatch(Connection c, int n) throws SQLException {
        ensureBenchIns(c);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bench_ins (id, v) VALUES (?, ?)")) {
            for (int i = 0; i < n; i++) {
                ps.setInt(1, i);
                ps.setString(2, "v-" + i);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void getColumns(Connection c) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, "bench_t", null)) {
            while (rs.next()) {
                rs.getString("COLUMN_NAME");
            }
        }
    }

    private static void prepareEach(Connection c, int times) throws SQLException {
        for (int i = 0; i < times; i++) {
            try (PreparedStatement ps = c.prepareStatement("SELECT name FROM bench_t WHERE id = ?")) {
                ps.setInt(1, i + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rs.getString(1);
                }
            }
        }
    }

    /**
     * Simulate per-row work (~0.1ms). With sync fetch this stalls 10x for next pages;
     * with async prefetch the network RTT overlaps with consumer work.
     */
    private static void selectWithWork(Connection c, int fetchSize) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.setFetchSize(fetchSize);
            try (ResultSet rs = s.executeQuery("SELECT id, name, value FROM bench_t ORDER BY id")) {
                long sink = 0;
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String name = rs.getString(2);
                    double v = rs.getDouble(3);
                    // ~0.1ms of busy work per row → ~100ms total for 1000 rows
                    // (sleep would be too coarse; busy-loop gives stable timing)
                    long until = System.nanoTime() + 100_000;
                    while (System.nanoTime() < until) {
                        sink ^= id ^ name.hashCode() ^ Double.doubleToLongBits(v);
                    }
                }
                if (sink == 0xDEADBEEF) System.out.print("");
            }
        }
    }

    private static void prepareReuse(Connection c, int times) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM bench_t WHERE id = ?")) {
            for (int i = 0; i < times; i++) {
                ps.setInt(1, i + 1);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rs.getString(1);
                }
            }
        }
    }

    // ===== timing harness =====

    @FunctionalInterface
    interface BenchOp {
        void run() throws SQLException;
    }

    @FunctionalInterface
    interface ConnBenchOp {
        void run(Connection c) throws SQLException;
    }

    private static Result measure(String name, int iters, BenchOp op) throws SQLException {
        op.run(); // warmup
        long[] samples = new long[iters];
        for (int i = 0; i < iters; i++) {
            long t = System.nanoTime();
            op.run();
            samples[i] = System.nanoTime() - t;
        }
        return new Result(name, samples);
    }

    private static Result measureOnConn(String name, int iters, ConnBenchOp op) throws SQLException {
        try (Connection c = connect()) {
            op.run(c); // warmup
            long[] samples = new long[iters];
            for (int i = 0; i < iters; i++) {
                long t = System.nanoTime();
                op.run(c);
                samples[i] = System.nanoTime() - t;
            }
            return new Result(name, samples);
        }
    }

    // ===== reporting =====

    record Result(String name, long[] samples) {
        long mean() {
            long sum = 0;
            for (long s : samples) sum += s;
            return sum / samples.length;
        }
        long pct(double p) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            int idx = (int) Math.min(sorted.length - 1, Math.round(p * (sorted.length - 1)));
            return sorted[idx];
        }
        long min() { return Arrays.stream(samples).min().orElse(0); }
        long max() { return Arrays.stream(samples).max().orElse(0); }
    }

    private static void printResults(List<Result> results) {
        System.out.printf(Locale.ROOT, "%-32s %5s %10s %10s %10s %10s %10s%n",
                "scenario", "n", "min(ms)", "p50(ms)", "mean(ms)", "p95(ms)", "max(ms)");
        System.out.println("-".repeat(96));
        for (Result r : results) {
            System.out.printf(Locale.ROOT, "%-32s %5d %10.1f %10.1f %10.1f %10.1f %10.1f%n",
                    r.name(), r.samples().length,
                    r.min() / 1_000_000.0,
                    r.pct(0.50) / 1_000_000.0,
                    r.mean() / 1_000_000.0,
                    r.pct(0.95) / 1_000_000.0,
                    r.max() / 1_000_000.0);
        }
    }
}
