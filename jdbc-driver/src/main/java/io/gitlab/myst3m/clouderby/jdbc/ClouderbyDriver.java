package io.gitlab.myst3m.clouderby.jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC Driver for clouderby (JDBC over HTTP).
 *
 * Connection URL formats:
 * - Remote HTTP: jdbc:clouderby://host:port/database
 * - Remote HTTPS: jdbc:clouderby://host:443/database?secure=true
 *
 * Examples:
 * - jdbc:clouderby://localhost:8080/mydb
 * - jdbc:clouderby://example.com:443/mydb?secure=true
 */
public class ClouderbyDriver implements Driver {
    private static final String URL_PREFIX = "jdbc:clouderby://";
    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

    static {
        try {
            DriverManager.registerDriver(new ClouderbyDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register ClouderbyDriver", e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        ParsedUrl parsed = parseUrl(url);

        // Extract user/password from properties
        String user = info != null ? info.getProperty("user") : null;
        String password = info != null ? info.getProperty("password") : null;

        return new ClouderbyConnection(parsed.host, parsed.port, parsed.database, parsed.secure, user, password, parsed.debug);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        DriverPropertyInfo userProp = new DriverPropertyInfo("user", null);
        userProp.description = "Username for authentication";
        userProp.required = false;

        DriverPropertyInfo passwordProp = new DriverPropertyInfo("password", null);
        passwordProp.description = "Password for authentication";
        passwordProp.required = false;

        return new DriverPropertyInfo[] { userProp, passwordProp };
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    private ParsedUrl parseUrl(String url) throws SQLException {
        // Format: jdbc:clouderby://host:port/database?params
        String remainder = url.substring(URL_PREFIX.length());

        // Split off query parameters
        String pathPart;
        String queryPart = null;
        int queryIndex = remainder.indexOf('?');
        if (queryIndex >= 0) {
            pathPart = remainder.substring(0, queryIndex);
            queryPart = remainder.substring(queryIndex + 1);
        } else {
            pathPart = remainder;
        }

        int slashIndex = pathPart.indexOf('/');
        if (slashIndex < 0) {
            throw new SQLException("Invalid URL format. Expected: jdbc:clouderby://host:port/database");
        }

        String hostPort = pathPart.substring(0, slashIndex);
        String database = pathPart.substring(slashIndex + 1);

        if (database.isEmpty()) {
            database = "default";
        }

        String host;
        int port;
        boolean portSpecified = false;

        int colonIndex = hostPort.indexOf(':');
        if (colonIndex < 0) {
            host = hostPort;
            port = 80; // Will be overridden to 443 if secure=true
        } else {
            host = hostPort.substring(0, colonIndex);
            portSpecified = true;
            try {
                port = Integer.parseInt(hostPort.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid port number in URL");
            }
        }

        if (host.isEmpty()) {
            host = "localhost";
        }

        // Parse query parameters
        boolean secure = false;
        boolean debug = false;

        if (queryPart != null && !queryPart.isEmpty()) {
            String[] params = queryPart.split("&");
            for (String param : params) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    switch (key) {
                        case "secure":
                            secure = "true".equalsIgnoreCase(value);
                            break;
                        case "debug":
                            debug = "true".equalsIgnoreCase(value);
                            break;
                    }
                }
            }
        }

        // If secure=true and port not specified, use 443
        if (secure && !portSpecified) {
            port = 443;
        }

        return new ParsedUrl(host, port, database, secure, debug);
    }

    private static class ParsedUrl {
        final String host;
        final int port;
        final String database;
        final boolean secure;
        final boolean debug;

        ParsedUrl(String host, int port, String database, boolean secure, boolean debug) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.secure = secure;
            this.debug = debug;
        }
    }
}
