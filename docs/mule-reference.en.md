# MuleSoft Mule 4 Reference Implementation

[日本語](mule-reference.md)

The clouderby protocol implemented on MuleSoft Mule 4, as two applications: a server and a client.

## Prerequisites

- Java 17
- Maven 3.8+
- Mule 4.10.1 or later (for standalone runs)

## Server (clouderby-mule-server)

A clouderby protocol server backed by **Apache Derby (embedded)**. It seeds a dataset
profile (schema + sample data) on startup and also ships a web admin UI.

### Build

```bash
cd reference/mule/clouderby-mule-server
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

Artifact: `target/mule-clouderby-1.15.0-mule-application.jar`
(the name follows `artifactId` / `version` in `pom.xml`)

### Run

**Mule standalone:**

```bash
cp target/mule-clouderby-*-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
~/srv/mule-enterprise-standalone-4.10.1/bin/mule start
```

**CloudHub 2.0 (yc CLI):**

```bash
yc deploy file <org> <env> <group> mule-clouderby 1.15.0 \
  target/mule-clouderby-1.15.0-mule-application.jar target=ps:<private-space>
```

### Configuration

Configuration is read from `src/main/resources/config/config-${mule.env}.yaml`.
`mule.env` defaults to `cloudhub` via a global-property in `global-config.xml`.

| Property | Description | Default |
|----------|-------------|---------|
| `http.port` | HTTP listener port | 8081 |
| `derby.path` | Embedded Derby database directory | /tmp/data/appdb |
| `clouderby.user` / `clouderby.password` | Credentials | mule / mule123 |
| `db.init.enabled` | Seed data on startup | true |
| `db.init.profile` | Profile to seed on startup | manufacturing |

Three config files ship with the app: `config-local.yaml`, `config-cloudhub.yaml`, `config-mulet.yaml`.

> The Derby location is also written in the `clouderbyDataSource` bean in
> `src/main/resources/spring-config.xml`. Change both if you move the path.

### Dataset profiles

A profile bundles a **schema (DDL) and its sample data (CSV)**. **Every profile is
loaded at startup, each into its own Derby schema**, so switching is a `SET SCHEMA`:
nothing is dropped and both datasets stay usable at all times.

| id | Schema | Contents | Tables | Rows |
|----|--------|----------|--------|------|
| `manufacturing` | `MANUFACTURING` | Materials manufacturer ERP: master data, inventory, sales, procurement, production, equipment, finance and R&D | 52 | 427 |
| `finance` | `FINANCE` | Retail banking: branches, customers (CIF), deposit accounts, transactions, cards, lending, credit risk and AML | 25 | 2,864 |

The schema name is the profile id upper-cased. Qualify the names and a single
query can span both:

```sql
SELECT (SELECT COUNT(*) FROM MANUFACTURING.INV_INVENTORY) AS INV,
       (SELECT COUNT(*) FROM FINANCE.ACC_ACCOUNTS) AS ACC FROM SYSIBM.SYSDUMMY1
```

```
src/main/resources/init/
├── profiles.json                  # catalog (defaultProfile + which profiles ship)
└── profiles/
    ├── manufacturing/
    │   ├── profile.json           # UI metadata + table load order
    │   ├── ddl.sql                # unqualified; run on a connection with the schema set
    │   ├── identity_restart.sql   # IDENTITY counter restarts
    │   └── csv/<TABLE>.csv
    └── finance/
        └── (same layout)
```

#### Choosing a schema at connect time

Name the profile in the JDBC URL path, or in the `database` field of
POST /sessions. Without one you get the server default.

```
jdbc:clouderby://host:443/finance?secure=true       -> FINANCE schema
jdbc:clouderby://host:443/manufacturing?secure=true -> MANUFACTURING schema
```

```bash
curl -X POST http://localhost:8081/sessions -H 'Content-Type: application/json' \
  -d '{"database":"finance","user":"mule","password":"mule123"}'
# -> {"session-id":"...","schema":"FINANCE","profile":"finance"}
```

#### Switching at runtime

From the **Profiles** tab in the admin UI, or through the API:

```bash
curl http://localhost:8081/api/profiles -H "X-Clouderby-Session-Id: $SID"

# switch what is in view (non-destructive)
curl -X POST http://localhost:8081/api/profiles/select \
  -H 'Content-Type: application/json' -H "X-Clouderby-Session-Id: $SID" \
  -d '{"profile":"finance"}'

# rebuild just that schema from the seed data (destructive, scoped to it)
curl -X POST http://localhost:8081/api/profiles/reload \
  -H 'Content-Type: application/json' -H "X-Clouderby-Session-Id: $SID" \
  -d '{"profile":"finance"}'
```

`select` changes **the calling session's schema** and **the default new sessions
get**. Sessions already open are left alone, rather than having the schema
changed under a running query. Both endpoints require a valid
`X-Clouderby-Session-Id`.

The default for new sessions is resolved in this order:

1. Whatever `select` last chose (recorded in `APP.CLOUDERBY_STATE`)
2. `db.init.profile` -- from the config file, or as a deploy-time application
   property (`yc deploy ... +db.init.profile=finance`), which takes precedence
   over the config file
3. `defaultProfile` in `/init/profiles.json`

`GET /api/profiles` reports which one won, under `startup`.

> **Note:** CloudHub 2.0's `/tmp` does not survive a restart. A `select` is
> recorded in `APP.CLOUDERBY_STATE`, but that table goes with it, so a restarted
> replica falls back to 2. or 3. above. To change the default for good, set it in
> the config file or as a deploy-time property.

To add a profile, create `profiles/<id>/` and add its id to the `profiles` array
in `profiles.json`. The `finance` data is generated by
`tools/gen_finance_data.py` (fixed seed, reproducible).

### Endpoints

**clouderby protocol (APIKit / `src/main/resources/api/clouderby-api.yaml`)**

| Path | Method | Description |
|------|--------|-------------|
| `/health` | GET | Health check |
| `/sessions` | POST / DELETE | Create / close a session |
| `/queries` | POST | Execute SQL |
| `/statements` | POST | Create a PreparedStatement |
| `/statements/{id}/execute` | POST | Execute a PreparedStatement |
| `/statements/{id}/batch` | POST | Execute a batch |
| `/statements/{id}/metadata` | GET | Result metadata |
| `/statements/{id}` | DELETE | Close |
| `/cursors/{id}/fetch` | POST | Fetch the next page of a cursor |
| `/cursors/{id}` | DELETE | Close a cursor |
| `/transactions/begin` \| `/commit` \| `/rollback` | POST | Transactions |
| `/metadata/info` \| `/tables` \| `/columns` \| `/primary-keys` | GET | DatabaseMetaData |
| `/auth/change-password` | POST | Change password |

**Admin UI and extension endpoints (outside the protocol)**

| Path | Method | Description |
|------|--------|-------------|
| `/` | GET | Admin UI (`src/main/resources/static/index.html`) |
| `/api/db/tables` | GET | Tables of the session's schema (for the UI; needs a session) |
| `/api/completions` | GET | Completion candidates for the session's schema (for the UI; needs a session) |
| `/api/profiles` | GET | Catalog, per-profile row counts, and the schema this session is on |
| `/api/profiles/select` | POST | Switch the schema in view (non-destructive) |
| `/api/profiles/reload` | POST | Rebuild one schema from its seed data |

### Smoke test

```bash
# Health check
curl http://localhost:8081/health

# Create a session
curl -X POST http://localhost:8081/sessions \
  -H "Content-Type: application/json" \
  -d '{"database": "app", "user": "mule", "password": "mule123"}'

# Run SQL
curl -X POST http://localhost:8081/queries \
  -H "Content-Type: application/json" \
  -H "X-Clouderby-Session-Id: <session-id>" \
  -d '{"sql": "SELECT * FROM INV_WAREHOUSES"}'
```

## Client (clouderby-mule-client)

An example Mule application that connects to a remote server through the clouderby JDBC driver.

### Build

```bash
cd reference/mule/clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

Artifact: `target/clouderby-mule-client-1.2.4-mule-application.jar`

### Configuration

`src/main/resources/config/config-local.yaml`:

```yaml
http:
  port: "8082"

clouderby:
  host: "clouderby-mule-server-xxxxxx.pnwfdv.jpn-e1.cloudhub.io"
  port: "443"
  database: "app"
  secure: "true"
  debug: "true"
  user: "mule"
  password: "mule123"
```

### Usage

The client points the DB Connector's generic-connection at the clouderby JDBC driver.

```xml
<db:config name="Database_Config">
    <db:generic-connection
        url="jdbc:clouderby://${clouderby.host}:${clouderby.port}/${clouderby.database}?secure=${clouderby.secure}&amp;debug=${clouderby.debug}"
        driverClassName="io.gitlab.myst3m.clouderby.jdbc.ClouderbyDriver" />
</db:config>
```

## Tests

### Integration test

```bash
# 1. Build and deploy the server
cd reference/mule/clouderby-mule-server
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 2. Check the server is up
curl http://localhost:8081/health

# 3. Build and deploy the client
cd ../clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
```

### MUnit tests

MUnit requires a MuleSoft EE license. With access to the EE repository:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test
```

## Layout

```
reference/mule/
├── clouderby-mule-server/            # server implementation
│   ├── pom.xml
│   ├── mule-artifact.json
│   ├── tools/
│   │   └── gen_finance_data.py       # generates the finance profile CSVs
│   └── src/main/
│       ├── java/com/muledev/
│       │   ├── init/
│       │   │   ├── DatabaseInitializer.java   # seeds a profile on startup
│       │   │   ├── ProfileManager.java        # lists and applies profiles
│       │   │   └── SqlExecutorInitializer.java
│       │   └── server/
│       │       ├── SqlExecutor.java           # static SQL methods called from flows
│       │       └── ClouderbySessionManager.java
│       ├── mule/
│       │   ├── api-implementation.xml
│       │   └── global-config.xml
│       └── resources/
│           ├── api/clouderby-api.yaml
│           ├── config/
│           ├── init/                          # dataset profiles
│           ├── spring-config.xml
│           └── static/index.html              # admin UI
│
└── clouderby-mule-client/            # example client
    ├── pom.xml
    ├── mule-artifact.json
    └── src/main/
        ├── mule/
        │   ├── api-implementation.xml
        │   └── global-config.xml
        └── resources/config/
```
