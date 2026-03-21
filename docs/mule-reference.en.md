# MuleSoft Mule 4 Reference Implementation

[日本語](mule-reference.md)

Reference implementation of the clouderby protocol using MuleSoft Mule 4.

## Prerequisites

- Java 17
- Maven 3.8+
- Mule 4.10.1 (for standalone execution)

## Server (clouderby-mule-app)

Implementation of an clouderby protocol server with SQLite backend.

### Build

```bash
cd reference/mule/clouderby-mule-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

Build artifact: `target/clouderby-server-app-1.0.0-SNAPSHOT-mule-application.jar`

### Run

**Mule Standalone:**

```bash
cp target/clouderby-server-app-1.0.0-SNAPSHOT-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
~/srv/mule-enterprise-standalone-4.10.1/bin/mule start
```

**Configuration:**

| Property | Description | Default |
|----------|-------------|---------|
| `http.port` | HTTP listener port | 8081 |
| `clouderby.data.dir` | SQLite file storage directory | ./data |

### Endpoints

| Path | Description |
|------|-------------|
| `GET /health` | Health check |
| `POST /sessions` | Create session |
| `DELETE /sessions` | Close session |
| `POST /queries` | Execute SQL |
| `POST /statements` | Create PreparedStatement |
| `POST /statements/{id}/execute` | Execute PreparedStatement |
| `GET /metadata/*` | Get metadata |

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

## Client (clouderby-mule-client)

Example Mule application that connects to a remote server using the clouderby JDBC driver.

### Build

```bash
cd reference/mule/clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

Build artifact: `target/clouderby-mule-client-1.0.0-SNAPSHOT-mule-application.jar`

### Configuration

`src/main/resources/config/config-local.yaml`:

```yaml
http:
  port: "8082"

clouderby:
  server:
    host: "localhost"
    port: "8081"
    database: "test"
    secure: "false"
```

### Run

```bash
cp target/clouderby-mule-client-1.0.0-SNAPSHOT-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
```

### Usage

The client app connects to the server via the clouderby JDBC driver:

```xml
<db:config name="Database_Config">
    <db:generic-connection
        url="jdbc:clouderby://${clouderby.server.host}:${clouderby.server.port}/${clouderby.server.database}?secure=${clouderby.server.secure}"
        driverClassName="com.muledev.ClouderbyDriver" />
</db:config>
```

## Testing

### Integration Test

Start both server and client for end-to-end testing.

```bash
# 1. Start server
cd reference/mule/clouderby-mule-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 2. Verify server
curl http://localhost:8081/health

# 3. Build and start client
cd ../clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 4. Test via client
curl http://localhost:8082/health
```

### MUnit Tests

MUnit tests require a MuleSoft EE license. If you have access to the EE repository:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test
```

## File Structure

```
reference/mule/
├── clouderby-mule-app/           # Server implementation
│   ├── pom.xml
│   ├── mule-artifact.json
│   └── src/main/
│       ├── java/com/muledev/
│       │   ├── init/DatabaseInitializer.java
│       │   └── server/
│       │       ├── ClouderbyApi.java
│       │       └── ClouderbySessionManager.java
│       ├── mule/
│       │   ├── api-implementation.xml
│       │   └── global-config.xml
│       └── resources/
│           ├── api/clouderby-api.yaml
│           ├── config/
│           └── static/index.html
│
└── clouderby-mule-client/        # Client example
    ├── pom.xml
    ├── mule-artifact.json
    └── src/main/
        ├── mule/
        │   ├── api-implementation.xml
        │   └── global-config.xml
        └── resources/config/
```
