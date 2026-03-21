# Clojure リファレンス実装

[English](clojure-reference.en.md)

clouderbyプロトコルのClojureによるリファレンス実装です。

## 前提条件

- Java 17+
- Clojure CLI (clj)

## サーバー (clouderby-clj-server)

SQLiteをバックエンドとする軽量なclouderbyプロトコルサーバーです。

### 実行

```bash
cd reference/clj/clouderby-clj-server
clj -M:run
```

環境変数で設定可能:

| 変数 | 説明 | デフォルト |
|-----|------|-----------|
| `PORT` | HTTPポート | 8081 |
| `CLOUDERBY_DATA_DIR` | SQLiteファイル保存先 | ./data |

```bash
PORT=8090 CLOUDERBY_DATA_DIR=/tmp/clouderby clj -M:run
```

### テスト

```bash
cd reference/clj/clouderby-clj-server
clj -M:test-runner -m cognitect.test-runner
```

テスト内容:
- ヘルスエンドポイント
- セッションライフサイクル (作成/終了)
- クエリ実行 (CREATE, INSERT, SELECT)
- PreparedStatement実行
- メタデータエンドポイント
- エラーハンドリング

### Uberjar ビルド

```bash
clj -X:uberjar
java -jar target/clouderby-server.jar
```

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

## クライアント (clj-client)

clouderbyプロトコルサーバーに接続するCLIクライアントです。

### 実行

```bash
cd reference/clj/clj-client

# 対話モード
clj -M:run http://localhost:8081 test

# SQLファイル実行
clj -M:run http://localhost:8081 test --file script.sql
```

### テスト

```bash
cd reference/clj/clj-client

# サーバーが起動している状態で実行
clj -M:test-runner -m cognitect.test-runner

# 別のサーバーに対してテスト
CLOUDERBY_TEST_URL=http://localhost:8090 clj -M:test-runner -m cognitect.test-runner
```

テスト内容:
- ヘルスチェック
- 接続管理
- SQL実行
- PreparedStatement
- エラーハンドリング

### ライブラリとして使用

```clojure
(require '[clouderby.client :as clouderby])

;; 接続
(def conn (clouderby/connect "http://localhost:8081" "mydb"))

;; SQL実行
(clouderby/execute conn "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT)")
(clouderby/execute conn "INSERT INTO users (name) VALUES ('Alice')")

(let [result (clouderby/execute conn "SELECT * FROM users")]
  (clouderby/print-result result))

;; PreparedStatement
(let [stmt (clouderby/prepare conn "INSERT INTO users (name) VALUES (?)")]
  (clouderby/execute-prepared stmt [{"index" 1 "type" "TEXT" "value" "Bob"}])
  (clouderby/close-statement stmt))

;; 切断
(clouderby/disconnect conn)
```

## ファイル構成

```
reference/clj/
├── clouderby-clj-server/         # サーバー実装
│   ├── deps.edn
│   ├── src/clouderby/server.clj
│   └── test/clouderby/server_test.clj
│
└── clj-client/                # CLIクライアント
    ├── deps.edn
    ├── src/clouderby/client.clj
    └── test/clouderby/client_test.clj
```

## 依存関係

### サーバー

- ring (HTTPサーバー)
- reitit (ルーティング)
- next.jdbc (JDBC)
- sqlite-jdbc (SQLiteドライバー)

### クライアント

- jackson-databind (JSON処理)
