# MuleSoft Mule 4 リファレンス実装

[English](mule-reference.en.md)

clouderbyプロトコルのMuleSoft Mule 4によるリファレンス実装です。

## 前提条件

- Java 17
- Maven 3.8+
- Mule 4.10.1 (スタンドアロン実行時)

## サーバー (clouderby-mule-app)

SQLiteをバックエンドとするclouderbyプロトコルサーバーの実装です。

### ビルド

```bash
cd reference/mule/clouderby-mule-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

ビルド成果物: `target/clouderby-server-app-1.0.0-SNAPSHOT-mule-application.jar`

### 実行

**Muleスタンドアロン:**

```bash
cp target/clouderby-server-app-1.0.0-SNAPSHOT-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
~/srv/mule-enterprise-standalone-4.10.1/bin/mule start
```

**設定:**

| プロパティ | 説明 | デフォルト |
|-----------|------|-----------|
| `http.port` | HTTPリスナーポート | 8081 |
| `clouderby.data.dir` | SQLiteファイル保存先 | ./data |

### エンドポイント

| パス | 説明 |
|------|------|
| `GET /health` | ヘルスチェック |
| `POST /sessions` | セッション作成 |
| `DELETE /sessions` | セッション終了 |
| `POST /queries` | SQL実行 |
| `POST /statements` | PreparedStatement作成 |
| `POST /statements/{id}/execute` | PreparedStatement実行 |
| `GET /metadata/*` | メタデータ取得 |

### 動作確認

```bash
# ヘルスチェック
curl http://localhost:8081/health

# セッション作成
curl -X POST http://localhost:8081/sessions \
  -H "Content-Type: application/json" \
  -d '{"database": "test"}'

# SQL実行
curl -X POST http://localhost:8081/queries \
  -H "Content-Type: application/json" \
  -H "X-Clouderby-Session-Id: <session-id>" \
  -d '{"sql": "SELECT 1"}'
```

## クライアント (clouderby-mule-client)

clouderby JDBCドライバーを使用してリモートサーバーに接続するMuleアプリケーションの例です。

### ビルド

```bash
cd reference/mule/clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

ビルド成果物: `target/clouderby-mule-client-1.0.0-SNAPSHOT-mule-application.jar`

### 設定

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

### 実行

```bash
cp target/clouderby-mule-client-1.0.0-SNAPSHOT-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
```

### 使用例

クライアントアプリはclouderby JDBCドライバー経由でサーバーに接続します:

```xml
<db:config name="Database_Config">
    <db:generic-connection
        url="jdbc:clouderby://${clouderby.server.host}:${clouderby.server.port}/${clouderby.server.database}?secure=${clouderby.server.secure}"
        driverClassName="com.muledev.ClouderbyDriver" />
</db:config>
```

## テスト

### 統合テスト

サーバーとクライアントを起動し、エンドツーエンドでテストします。

```bash
# 1. サーバー起動
cd reference/mule/clouderby-mule-app
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 2. サーバー起動確認
curl http://localhost:8081/health

# 3. クライアントビルド・起動
cd ../clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 4. クライアント経由でテスト
curl http://localhost:8082/health
```

### MUnitテスト

MUnitテストにはMuleSoft EEライセンスが必要です。EEリポジトリへのアクセス権がある場合:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test
```

## ファイル構成

```
reference/mule/
├── clouderby-mule-app/           # サーバー実装
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
└── clouderby-mule-client/        # クライアント例
    ├── pom.xml
    ├── mule-artifact.json
    └── src/main/
        ├── mule/
        │   ├── api-implementation.xml
        │   └── global-config.xml
        └── resources/config/
```
