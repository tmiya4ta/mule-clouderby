# clouderby - JDBC over HTTP Protocol

[![Maven Central](https://img.shields.io/maven-central/v/io.gitlab.myst3m/clouderby-jdbc)](https://central.sonatype.com/artifact/io.gitlab.myst3m/clouderby-jdbc)

[English](README.md)

HTTP/HTTPS経由でJDBCを実現するプロトコル仕様とリファレンス実装。

## 概要

clouderbyは「JDBC over HTTP」を実現するためのオープンプロトコルです。

```
┌─────────────────┐                      ┌─────────────────┐
│  Java Client    │    HTTP/HTTPS        │  Any Server     │
│  (JDBC Driver)  │ ←─────────────────→  │  implementing   │
│                 │   clouderby protocol    │  the protocol   │
│ jdbc:clouderby://  │                      │       ↓         │
└─────────────────┘                      │  Any Database   │
                                         └─────────────────┘
```

### コンポーネント

| コンポーネント | 説明 |
|---------------|------|
| **spec/** | OpenAPI 3.0で定義されたプロトコル仕様 |
| **jdbc-driver/** | プロトコルを話すJDBCドライバー (Maven Central公開済み) |
| **reference/** | リファレンス実装 (サーバー・クライアント) |

## プロトコル仕様

`spec/clouderby-api.yaml` にOpenAPI 3.0形式で定義。セッションIDは `X-Clouderby-Session-Id` ヘッダーで管理。

各HTTPエンドポイントはJDBCインターフェースの以下のメソッドに対応:

| エンドポイント | メソッド | JDBCインターフェース | 対応メソッド |
|---------------|---------|---------------------|-------------|
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
| `/metadata/info` | GET | `DatabaseMetaData` | `getDatabaseProductName()`, `getDriverVersion()` 等 |
| `/metadata/tables` | GET | `DatabaseMetaData` | `getTables()` |
| `/metadata/columns` | GET | `DatabaseMetaData` | `getColumns()` |
| `/metadata/primary-keys` | GET | `DatabaseMetaData` | `getPrimaryKeys()` |

## JDBCドライバー

### インストール

**Maven:**
```xml
<dependency>
    <groupId>io.gitlab.myst3m</groupId>
    <artifactId>clouderby-jdbc</artifactId>
    <version>1.5.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.gitlab.myst3m:clouderby-jdbc:1.5.0'
```

### 接続URL

```
jdbc:clouderby://host:port/database[?secure=true&debug=true]
```

| オプション | 説明 | デフォルト |
|-----------|------|-----------|
| `secure` | HTTPS使用 | false |
| `debug` | SQL・HTTP通信をstderrにログ出力 | false |

### 使用例

```java
// プロトコル準拠サーバーに接続
String url = "jdbc:clouderby://api.example.com:443/mydb?secure=true";
Connection conn = DriverManager.getConnection(url);

Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM users");
while (rs.next()) {
    System.out.println(rs.getString("name"));
}

conn.close();
```

### トランザクション

```java
Connection conn = DriverManager.getConnection(url);

// トランザクション開始
conn.setAutoCommit(false);

try {
    Statement stmt = conn.createStatement();
    stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (1, 1000)");
    stmt.executeUpdate("INSERT INTO accounts (id, balance) VALUES (2, 2000)");

    // コミット
    conn.commit();
} catch (SQLException e) {
    // ロールバック
    conn.rollback();
}

conn.setAutoCommit(true);
conn.close();
```

## ディレクトリ構成

```
clouderby/
├── spec/                     # プロトコル仕様 (OpenAPI 3.0)
│   └── clouderby-api.yaml
│
├── jdbc-driver/              # JDBCドライバー実装
│   └── src/main/java/com/muledev/
│
├── reference/                # リファレンス実装
│   ├── mule/                 # MuleSoft Mule 4
│   │   ├── clouderby-mule-app/      # サーバー
│   │   └── clouderby-mule-client/   # クライアント
│   └── clj/                  # Clojure
│       ├── clouderby-clj-server/    # サーバー
│       └── clj-client/           # CLIクライアント
│
└── docs/                     # ドキュメント
```

## リファレンス実装

- [MuleSoft Mule 4](docs/mule-reference.md) - サーバー・クライアント
- [Clojure](docs/clojure-reference.md) - サーバー・CLIクライアント

## ビルド

**Java 17必須**

```bash
# JDBCドライバー
cd jdbc-driver
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean install
```

リファレンス実装のビルド方法は各ドキュメントを参照:
- [MuleSoft Mule 4](docs/mule-reference.md#ビルド)
- [Clojure](docs/clojure-reference.md#実行)

## 型マッピング

| プロトコル型 | JSON表現 | JDBC |
|-------------|----------|------|
| INTEGER | number | getLong/getInt |
| REAL | number | getDouble |
| TEXT | string | getString |
| BLOB | base64 string | getBytes |
| NULL | null | wasNull() |

## ライセンス

[Eclipse Public License 2.0](LICENSE)
