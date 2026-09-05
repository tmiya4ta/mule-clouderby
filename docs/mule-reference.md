# MuleSoft Mule 4 リファレンス実装

[English](mule-reference.en.md)

clouderbyプロトコルのMuleSoft Mule 4による実装です。サーバーとクライアントの2アプリからなります。

## 前提条件

- Java 17
- Maven 3.8+
- Mule 4.10.1 以降 (スタンドアロン実行時)

## サーバー (clouderby-mule-server)

**Apache Derby (埋め込み)** をバックエンドとするclouderbyプロトコルサーバーです。
起動時にデータセットプロファイル (スキーマ + 初期データ) を投入し、Webの管理UIも同梱しています。

### ビルド

```bash
cd reference/mule/clouderby-mule-server
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

ビルド成果物: `target/mule-clouderby-1.16.0-mule-application.jar`
(名前は `pom.xml` の `artifactId` / `version` に従います)

### 実行

**Muleスタンドアロン:**

```bash
cp target/mule-clouderby-*-mule-application.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
~/srv/mule-enterprise-standalone-4.10.1/bin/mule start
```

**CloudHub 2.0 (yc CLI):**

```bash
yc deploy file <org> <env> <group> mule-clouderby 1.16.0 \
  target/mule-clouderby-1.16.0-mule-application.jar target=ps:<private-space>
```

### 設定

設定は `src/main/resources/config/config-${mule.env}.yaml` から読み込まれます。
`mule.env` の既定値は `global-config.xml` の global-property で `cloudhub` です。

| プロパティ | 説明 | デフォルト |
|-----------|------|-----------|
| `http.port` | HTTPリスナーポート | 8081 |
| `derby.path` | Derby埋め込みDBのディレクトリ | /tmp/data/appdb |
| `clouderby.user` / `clouderby.password` | 認証情報 | mule / mule123 |
| `db.init.enabled` | 起動時のデータ投入 | true |
| `db.init.profile` | 起動時に投入するプロファイル | manufacturing |

同梱の設定ファイルは `config-local.yaml` / `config-cloudhub.yaml` / `config-mulet.yaml` の3つです。

> Derbyの接続先は `src/main/resources/spring-config.xml` の `clouderbyDataSource` Bean にも
> 書かれています。パスを変える場合は両方あわせてください。

### データセットプロファイル

プロファイルは **スキーマ (DDL) + 初期データ (CSV)** をひとまとめにした単位です。
**すべてのプロファイルが起動時にロードされ、それぞれ専用の Derby スキーマに入ります。**
切り替えは `SET SCHEMA` なので、データは消えず、いつでも両方使えます。

| id | スキーマ | 内容 | テーブル数 | 行数 |
|----|---------|------|-----------|------|
| `manufacturing` | `MANUFACTURING` | 素材メーカーの基幹システム。マスタ・在庫・販売・購買・製造・設備・財務会計・研究開発 | 52 | 427 |
| `finance` | `FINANCE` | リテールバンキング。拠点・顧客(CIF)・預金口座・取引・カード・融資・与信・マネロン対策 | 25 | 2,864 |

プロファイル id を大文字にしたものがスキーマ名です。修飾すれば1つのクエリで両方を跨げます。

```sql
SELECT (SELECT COUNT(*) FROM MANUFACTURING.INV_INVENTORY) AS INV,
       (SELECT COUNT(*) FROM FINANCE.ACC_ACCOUNTS) AS ACC FROM SYSIBM.SYSDUMMY1
```

```
src/main/resources/init/
├── profiles.json                  # カタログ (defaultProfile と収録プロファイル)
└── profiles/
    ├── manufacturing/
    │   ├── profile.json           # UI用メタデータ + テーブルのロード順
    │   ├── ddl.sql                # 修飾なし。スキーマを設定した接続で流す
    │   ├── identity_restart.sql   # IDENTITY 採番のリスタート
    │   └── csv/<TABLE>.csv
    └── finance/
        └── (同じ構成)
```

#### 接続時にスキーマを選ぶ

JDBC URL のパス、または `POST /sessions` の `database` でプロファイルを指定します。
未指定ならサーバの既定スキーマになります。

```
jdbc:clouderby://host:443/finance?secure=true      -> FINANCE スキーマ
jdbc:clouderby://host:443/manufacturing?secure=true -> MANUFACTURING スキーマ
```

```bash
curl -X POST http://localhost:8081/sessions -H 'Content-Type: application/json' \
  -d '{"database":"finance","user":"mule","password":"mule123"}'
# -> {"session-id":"...","schema":"FINANCE","profile":"finance"}
```

#### 実行中に切り替える

管理UIの **Profiles** タブ、または API から。

```bash
curl http://localhost:8081/api/profiles -H "X-Clouderby-Session-Id: $SID"

# 表示の切り替え (非破壊)
curl -X POST http://localhost:8081/api/profiles/select \
  -H 'Content-Type: application/json' -H "X-Clouderby-Session-Id: $SID" \
  -d '{"profile":"finance"}'

# そのスキーマだけを初期データで作り直す (破壊的、他プロファイルには影響なし)
curl -X POST http://localhost:8081/api/profiles/reload \
  -H 'Content-Type: application/json' -H "X-Clouderby-Session-Id: $SID" \
  -d '{"profile":"finance"}'
```

`select` は **そのセッションのスキーマ** と **新規接続の既定** を変えます。
接続中の他セッションはそのままです (実行中のクエリの足元でスキーマを変えないため)。
どちらも有効な `X-Clouderby-Session-Id` が必要です。

新規接続の既定は、次の優先順で決まります。

1. `select` で選ばれた値 (DBの `APP.CLOUDERBY_STATE` に記録)
2. `db.init.profile` — 設定ファイル、またはデプロイ時のアプリケーションプロパティ
   (`yc deploy ... +db.init.profile=finance`)。後者が設定ファイルより優先されます
3. `/init/profiles.json` の `defaultProfile`

どれが効いたかは `GET /api/profiles` の `startup` で確認できます。

> **注意:** CloudHub 2.0 の `/tmp` は再起動で消えます。`select` の結果は
> `APP.CLOUDERBY_STATE` に記録されますが、その表ごと消えるため、レプリカが
> 再起動すると 2. または 3. の値に戻ります。恒久的に既定を変えるなら
> 設定ファイルかデプロイ時プロパティで指定してください。

新しいプロファイルを足すには、`profiles/<id>/` を作って `profiles.json` の
`profiles` 配列にidを追加します。`finance` のデータは
`tools/gen_finance_data.py` (シード固定) で生成しています。

### エンドポイント

**clouderbyプロトコル (APIKit / `src/main/resources/api/clouderby-api.yaml`)**

| パス | メソッド | 説明 |
|------|---------|------|
| `/health` | GET | ヘルスチェック |
| `/sessions` | POST / DELETE | セッション作成・終了 |
| `/queries` | POST | SQL実行 |
| `/statements` | POST | PreparedStatement作成 |
| `/statements/{id}/execute` | POST | PreparedStatement実行 |
| `/statements/{id}/batch` | POST | バッチ実行 |
| `/statements/{id}/metadata` | GET | 結果メタデータ |
| `/statements/{id}` | DELETE | クローズ |
| `/cursors/{id}/fetch` | POST | カーソルの続き取得 |
| `/cursors/{id}` | DELETE | カーソルのクローズ |
| `/transactions/begin` \| `/commit` \| `/rollback` | POST | トランザクション |
| `/metadata/info` \| `/tables` \| `/columns` \| `/primary-keys` | GET | DatabaseMetaData |
| `/auth/change-password` | POST | パスワード変更 |

**管理UI・拡張エンドポイント (プロトコル外)**

| パス | メソッド | 説明 |
|------|---------|------|
| `/` | GET | 管理UI (`src/main/resources/static/index.html`)。Tables タブに Console / DDL / **ER** のサブタブ |
| `/api/db/tables` | GET | 現在スキーマのテーブル一覧 (UI用、要セッション) |
| `/api/completions` | GET | 現在スキーマのSQL補完候補 (UI用、要セッション) |
| `/api/profiles` | GET | カタログ、各プロファイルの件数、このセッションが見ているスキーマ |
| `/api/profiles/select` | POST | 表示スキーマの切り替え (非破壊) |
| `/api/profiles/reload` | POST | 1スキーマだけを初期データで作り直す |

### 動作確認

```bash
# ヘルスチェック
curl http://localhost:8081/health

# セッション作成
curl -X POST http://localhost:8081/sessions \
  -H "Content-Type: application/json" \
  -d '{"database": "app", "user": "mule", "password": "mule123"}'

# SQL実行
curl -X POST http://localhost:8081/queries \
  -H "Content-Type: application/json" \
  -H "X-Clouderby-Session-Id: <session-id>" \
  -d '{"sql": "SELECT * FROM INV_WAREHOUSES"}'   # セッションのスキーマで解決される
```

## クライアント (clouderby-mule-client)

clouderby JDBCドライバー経由でリモートサーバーに接続するMuleアプリケーションの例です。

### ビルド

```bash
cd reference/mule/clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
```

ビルド成果物: `target/clouderby-mule-client-1.2.4-mule-application.jar`

### 設定

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

### 使用例

クライアントアプリはDB Connectorのgeneric-connectionにclouderby JDBCドライバーを指定します。

```xml
<db:config name="Database_Config">
    <db:generic-connection
        url="jdbc:clouderby://${clouderby.host}:${clouderby.port}/${clouderby.database}?secure=${clouderby.secure}&amp;debug=${clouderby.debug}"
        driverClassName="io.gitlab.myst3m.clouderby.jdbc.ClouderbyDriver" />
</db:config>
```

## テスト

### 統合テスト

```bash
# 1. サーバービルド・起動
cd reference/mule/clouderby-mule-server
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/

# 2. サーバー起動確認
curl http://localhost:8081/health

# 3. クライアントビルド・起動
cd ../clouderby-mule-client
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn clean package
cp target/*.jar ~/srv/mule-enterprise-standalone-4.10.1/apps/
```

### MUnitテスト

MUnitテストにはMuleSoft EEライセンスが必要です。EEリポジトリへのアクセス権がある場合:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn test
```

## ファイル構成

```
reference/mule/
├── clouderby-mule-server/            # サーバー実装
│   ├── pom.xml
│   ├── mule-artifact.json
│   ├── tools/
│   │   └── gen_finance_data.py       # finance プロファイルのCSV生成
│   └── src/main/
│       ├── java/com/muledev/
│       │   ├── init/
│       │   │   ├── DatabaseInitializer.java   # 起動時のプロファイル投入
│       │   │   ├── ProfileManager.java        # プロファイルの列挙・適用
│       │   │   └── SqlExecutorInitializer.java
│       │   └── server/
│       │       ├── SqlExecutor.java           # フローから呼ぶ静的SQLメソッド
│       │       └── ClouderbySessionManager.java
│       ├── mule/
│       │   ├── api-implementation.xml
│       │   └── global-config.xml
│       └── resources/
│           ├── api/clouderby-api.yaml
│           ├── config/
│           ├── init/                          # データセットプロファイル
│           ├── spring-config.xml
│           └── static/index.html              # 管理UI
│
└── clouderby-mule-client/            # クライアント例
    ├── pom.xml
    ├── mule-artifact.json
    └── src/main/
        ├── mule/
        │   ├── api-implementation.xml
        │   └── global-config.xml
        └── resources/config/
```
