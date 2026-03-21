# Clojure Reference Implementation

[日本語](clojure-reference.md)

Reference implementation of the clouderby protocol in Clojure.

## Prerequisites

- Java 17+
- Clojure CLI (clj)

## Server (clouderby-clj-server)

A lightweight clouderby protocol server with SQLite backend.

### Run

```bash
cd reference/clj/clouderby-clj-server
clj -M:run
```

Configurable via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | HTTP port | 8081 |
| `CLOUDERBY_DATA_DIR` | SQLite file storage directory | ./data |

```bash
PORT=8090 CLOUDERBY_DATA_DIR=/tmp/clouderby clj -M:run
```

### Test

```bash
cd reference/clj/clouderby-clj-server
clj -M:test-runner -m cognitect.test-runner
```

Test coverage:
- Health endpoint
- Session lifecycle (create/close)
- Query execution (CREATE, INSERT, SELECT)
- PreparedStatement execution
- Metadata endpoints
- Error handling

### Uberjar Build

```bash
clj -X:uberjar
java -jar target/clouderby-server.jar
```

### Verification

```bash
# Health check
curl http://localhost:8081/health

# Create session
curl -X POST http://localhost:8081/sessions \
  -H "Content-Type: application/json" \
  -d '{"database": "test"}'

# Execute SQL
curl -X POST http://localhost:8081/queries \
  -H "Content-Type: application/json" \
  -H "X-Clouderby-Session-Id: <session-id>" \
  -d '{"sql": "SELECT 1"}'
```

## Client (clj-client)

A CLI client that connects to clouderby protocol servers.

### Run

```bash
cd reference/clj/clj-client

# Interactive mode
clj -M:run http://localhost:8081 test

# Execute SQL file
clj -M:run http://localhost:8081 test --file script.sql
```

### Test

```bash
cd reference/clj/clj-client

# Run with server already started
clj -M:test-runner -m cognitect.test-runner

# Test against a different server
CLOUDERBY_TEST_URL=http://localhost:8090 clj -M:test-runner -m cognitect.test-runner
```

Test coverage:
- Health check
- Connection management
- SQL execution
- PreparedStatement
- Error handling

### Use as Library

```clojure
(require '[clouderby.client :as clouderby])

;; Connect
(def conn (clouderby/connect "http://localhost:8081" "mydb"))

;; Execute SQL
(clouderby/execute conn "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
(clouderby/execute conn "INSERT INTO users (name) VALUES ('Alice')")

(let [result (clouderby/execute conn "SELECT * FROM users")]
  (clouderby/print-result result))

;; PreparedStatement
(let [stmt (clouderby/prepare conn "INSERT INTO users (name) VALUES (?)")]
  (clouderby/execute-prepared stmt [{"index" 1 "type" "TEXT" "value" "Bob"}])
  (clouderby/close-statement stmt))

;; Disconnect
(clouderby/disconnect conn)
```

## File Structure

```
reference/clj/
├── clouderby-clj-server/         # Server implementation
│   ├── deps.edn
│   ├── src/clouderby/server.clj
│   └── test/clouderby/server_test.clj
│
└── clj-client/                # CLI client
    ├── deps.edn
    ├── src/clouderby/client.clj
    └── test/clouderby/client_test.clj
```

## Dependencies

### Server

- ring (HTTP server)
- reitit (routing)
- next.jdbc (JDBC)
- sqlite-jdbc (SQLite driver)

### Client

- jackson-databind (JSON processing)
