# clouderby - JDBC over HTTP Protocol

[![Maven Central](https://img.shields.io/maven-central/v/io.gitlab.myst3m/clouderby-jdbc)](https://central.sonatype.com/artifact/io.gitlab.myst3m/clouderby-jdbc)

[日本語](README.ja.md)

An open protocol specification and reference implementation for JDBC over HTTP/HTTPS.

## Overview

clouderby is an open protocol for "JDBC over HTTP".

```
┌─────────────────┐                      ┌─────────────────┐
│  Java Client    │    HTTP/HTTPS        │  Any Server     │
│  (JDBC Driver)  │ ←─────────────────→  │  implementing   │
│                 │   clouderby protocol    │  the protocol   │
│ jdbc:clouderby://  │                      │       ↓         │
└─────────────────┘                      │  Any Database   │
                                         └─────────────────┘
```

### Components

| Component | Description |
|-----------|-------------|
| **spec/** | Protocol specification defined in OpenAPI 3.0 |
| **jdbc-driver/** | JDBC driver that speaks the protocol (published on Maven Central) |
| **reference/** | Reference implementations (server & client) |

## Protocol Specification

Defined in `spec/clouderby-api.yaml` (OpenAPI 3.0 format). Session ID is managed via `X-Clouderby-Session-Id` header.

Each HTTP endpoint maps to JDBC interface methods as follows:

| Endpoint | Method | JDBC Interface | Mapped Method |
|----------|--------|---------------|---------------|
| `/sessions` | POST | `Connection` | `DriverManager.getConnection()` |
| `/sessions` | DELETE | `Connection` | `close()` |
| `/health` | GET | `Connection` | `isValid()` |
| `/queries` | POST | `Statement` | `executeQuery()`, `executeUpdate()`, `execute()` |
| `/statements` | POST | `Connection` | `prepareStatement()` |
| `/statements/{id}/execute` | POST | `PreparedStatement` | `executeQuery()`, `executeUpdate()` |
| `/statements/{id}/batch` | POST | `PreparedStatement` | `executeBatch()` |
| `/statements/{id}/metadata` | GET | `PreparedStatement` | `getMetaData()` |
| `/statements/{id}` | DELETE | `PreparedStatement` | `close()` |
| `/transactions/begin` | POST | `Connection` | `setAutoCommit(false)` |
| `/transactions/commit` | POST | `Connection` | `commit()` |
| `/transactions/rollback` | POST | `Connection` | `rollback()` |
| `/metadata/info` | GET | `DatabaseMetaData` | `getDatabaseProductName()`, `getDriverVersion()` etc. |
| `/metadata/tables` | GET | `DatabaseMetaData` | `getTables()` |
| `/metadata/columns` | GET | `DatabaseMetaData` | `getColumns()` |
| `/metadata/primary-keys` | GET | `DatabaseMetaData` | `getPrimaryKeys()` |

## JDBC Driver

### Installation

**Maven:**
```xml
<dependency>
    <groupId>io.gitlab.myst3m</groupId>
    <artifactId>clouderby-jdbc</artifactId>
    <version>1.3.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.gitlab.myst3m:clouderby-jdbc:1.3.0'
```

### Connection URL

```
jdbc:clouderby://host:port/database[?secure=true&debug=true]
```

| Option | Description | Default |
|--------|-------------|---------|
| `secure` | Use HTTPS | false |
| `debug` | Log SQL and HTTP requests to stderr | false |

### Usage

```java
// Connect to a protocol-compliant server
String url = "jdbc:clouderby://api.example.com:443/mydb?secure=true";
Connection conn = DriverManager.getConnection(url);

Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM users");
while (rs.next()) {
    System.out.println(rs.getString("name"));
}

conn.close();
```

### Transactions

```java
Connection conn = DriverManager.getConnection(url);

// Begin transaction
conn.setAutoCommit(false);

try {
    Statement stmt = conn.createStatement();
    stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (1, 1000)");
    stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (2, 2000)");

    // Commit
    conn.commit();
} catch (SQLException e) {
    // Rollback
    conn.rollback();
}

conn.setAutoCommit(true);
conn.close();
```

## Directory Structure

```
clouderby/
├── spec/                     # Protocol specification (OpenAPI 3.0)
│   └── clouderby-api.yaml
│
├── jdbc-driver/              # JDBC driver implementation
│   └── src/main/java/com/muledev/
│
├── reference/                # Reference implementations
│   ├── mule/                 # MuleSoft Mule 4
│   │   ├── clouderby-mule-app/      # Server
│   │   └── clouderby-mule-client/   # Client
│   └── clj/                  # Clojure
│       ├── clouderby-clj-server/    # Server
│       └── clj-client/           # CLI client
│
└── docs/                     # Documentation
```

## Reference Implementations

- [MuleSoft Mule 4](docs/mule-reference.en.md) - Server & Client
- [Clojure](docs/clojure-reference.en.md) - Server & CLI Client

## Build

**Requires Java 17**

```bash
# JDBC driver
cd jdbc-driver
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

For building reference implementations, see:
- [MuleSoft Mule 4](docs/mule-reference.en.md#build)
- [Clojure](docs/clojure-reference.en.md#run)

## Type Mapping

| Protocol Type | JSON | JDBC |
|---------------|------|------|
| INTEGER | number | getLong/getInt |
| REAL | number | getDouble |
| TEXT | string | getString |
| BLOB | base64 string | getBytes |
| NULL | null | wasNull() |

## License

[Eclipse Public License 2.0](LICENSE)
