(ns clouderby.server-test
  (:require [clojure.test :refer :all]
            [clouderby.server :as server]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]))

;; Use the wrapped handler for tests
(def test-handler server/handler)

(defn- json-request [method path body session-id]
  (let [req (cond-> (mock/request method path)
              true (mock/content-type "application/json")
              body (mock/body (json/write-str body))
              session-id (mock/header "X-Clouderby-Session-Id" session-id))]
    req))

(defn- parse-body [response]
  (let [body (:body response)]
    (cond
      (nil? body) nil
      (string? body) (json/read-str body)
      (map? body) (json/read-str (json/write-str body))
      :else body)))

(deftest health-test
  (testing "Health endpoint returns UP"
    (let [response (test-handler (mock/request :get "/health"))
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "UP" (get body "status")))
      (is (= "clouderby-clj-server" (get body "service"))))))

(deftest session-lifecycle-test
  (testing "Create and close session"
    ;; Create session
    (let [response (test-handler (json-request :post "/sessions" {"database" "test_session"} nil))
          body (parse-body response)
          session-id (get body "session-id")]
      (is (= 200 (:status response)))
      (is (some? session-id))
      (is (= "1.0.0" (get body "server-version")))

      ;; Close session
      (let [close-response (test-handler (json-request :delete "/sessions" nil session-id))
            close-body (parse-body close-response)]
        (is (= 200 (:status close-response)))
        (is (true? (get close-body "closed")))))))

(deftest query-execution-test
  (testing "Execute SQL queries"
    ;; Create session
    (let [response (test-handler (json-request :post "/sessions" {"database" "query_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Create table
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "CREATE TABLE IF NOT EXISTS test_tbl (id INTEGER PRIMARY KEY, name TEXT)"}
                                            session-id))
            body (parse-body res)]
        (is (= 200 (:status res)))
        (is (contains? body "update-count")))

      ;; Insert data
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "INSERT INTO test_tbl (name) VALUES ('Alice')"}
                                            session-id))
            body (parse-body res)]
        (is (= 200 (:status res)))
        (is (= 1 (get body "update-count"))))

      ;; Select data
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "SELECT * FROM test_tbl WHERE name = 'Alice'"}
                                            session-id))
            body (parse-body res)]
        (is (= 200 (:status res)))
        (is (>= (count (get body "rows")) 1))
        (is (= "Alice" (second (first (get body "rows"))))))

      ;; Cleanup
      (test-handler (json-request :post "/queries" {"sql" "DROP TABLE test_tbl"} session-id))
      (test-handler (json-request :delete "/sessions" nil session-id)))))

(deftest prepared-statement-test
  (testing "Prepared statement execution"
    ;; Create session
    (let [response (test-handler (json-request :post "/sessions" {"database" "prep_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Create table
      (test-handler (json-request :post "/queries"
                                  {"sql" "CREATE TABLE IF NOT EXISTS prep_tbl (id INTEGER PRIMARY KEY, name TEXT, value REAL)"}
                                  session-id))

      ;; Create prepared statement
      (let [stmt-res (test-handler (json-request :post "/statements"
                                                 {"sql" "INSERT INTO prep_tbl (name, value) VALUES (?, ?)"}
                                                 session-id))
            stmt-body (parse-body stmt-res)
            stmt-id (get stmt-body "statement-id")]
        (is (= 200 (:status stmt-res)))
        (is (some? stmt-id))
        (is (= 2 (get stmt-body "param-count")))

        ;; Execute with parameters
        (let [exec-res (test-handler (json-request :post (str "/statements/" stmt-id "/execute")
                                                   {"params" [{"index" 1 "type" "TEXT" "value" "Bob"}
                                                              {"index" 2 "type" "REAL" "value" 3.14}]}
                                                   session-id))
              exec-body (parse-body exec-res)]
          (is (= 200 (:status exec-res)))
          (is (= 1 (get exec-body "update-count"))))

        ;; Close statement
        (let [close-res (test-handler (json-request :delete (str "/statements/" stmt-id) nil session-id))]
          (is (= 200 (:status close-res)))))

      ;; Verify data
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "SELECT name, value FROM prep_tbl WHERE name = 'Bob'"}
                                            session-id))
            body (parse-body res)
            row (first (get body "rows"))]
        (is (= "Bob" (first row)))
        (is (= 3.14 (second row))))

      ;; Cleanup
      (test-handler (json-request :post "/queries" {"sql" "DROP TABLE prep_tbl"} session-id))
      (test-handler (json-request :delete "/sessions" nil session-id)))))

(deftest metadata-test
  (testing "Metadata endpoints"
    ;; Create session
    (let [response (test-handler (json-request :post "/sessions" {"database" "meta_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Create table for metadata test
      (test-handler (json-request :post "/queries"
                                  {"sql" "CREATE TABLE IF NOT EXISTS meta_tbl (id INTEGER PRIMARY KEY)"}
                                  session-id))

      ;; Get metadata info
      (let [res (test-handler (-> (mock/request :get "/metadata/info")
                                  (mock/header "X-Clouderby-Session-Id" session-id)))
            body (parse-body res)]
        (is (= 200 (:status res)))
        (is (= "clouderby-clj-server" (get body "product-name"))))

      ;; Get tables
      (let [res (test-handler (-> (mock/request :get "/metadata/tables")
                                  (mock/header "X-Clouderby-Session-Id" session-id)))
            body (parse-body res)
            tables (get body "tables")]
        (is (= 200 (:status res)))
        (is (some #(= "meta_tbl" (get % "name")) tables)))

      ;; Cleanup
      (test-handler (json-request :post "/queries" {"sql" "DROP TABLE meta_tbl"} session-id))
      (test-handler (json-request :delete "/sessions" nil session-id)))))

(deftest error-handling-test
  (testing "Missing session ID"
    (let [res (test-handler (json-request :post "/queries" {"sql" "SELECT 1"} nil))]
      (is (= 400 (:status res)))))

  (testing "Invalid session ID"
    (let [res (test-handler (json-request :post "/queries" {"sql" "SELECT 1"} "invalid-session"))]
      (is (= 404 (:status res)))))

  (testing "SQL error"
    (let [response (test-handler (json-request :post "/sessions" {"database" "error_test"} nil))
          session-id (get (parse-body response) "session-id")
          res (test-handler (json-request :post "/queries" {"sql" "INVALID SQL SYNTAX"} session-id))]
      (is (= 500 (:status res)))
      (test-handler (json-request :delete "/sessions" nil session-id)))))

(deftest transaction-test
  (testing "Transaction commit"
    (let [response (test-handler (json-request :post "/sessions" {"database" "tx_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Create table
      (test-handler (json-request :post "/queries"
                                  {"sql" "CREATE TABLE IF NOT EXISTS tx_tbl (id INTEGER PRIMARY KEY, name TEXT)"}
                                  session-id))

      ;; Begin transaction
      (let [begin-res (test-handler (json-request :post "/transactions/begin" nil session-id))
            begin-body (parse-body begin-res)]
        (is (= 200 (:status begin-res)))
        (is (= "STARTED" (get begin-body "status")))
        (is (true? (get begin-body "in-transaction"))))

      ;; Insert data in transaction
      (test-handler (json-request :post "/queries"
                                  {"sql" "INSERT INTO tx_tbl (name) VALUES ('Committed')"}
                                  session-id))

      ;; Commit transaction
      (let [commit-res (test-handler (json-request :post "/transactions/commit" nil session-id))
            commit-body (parse-body commit-res)]
        (is (= 200 (:status commit-res)))
        (is (= "COMMITTED" (get commit-body "status")))
        (is (false? (get commit-body "in-transaction"))))

      ;; Verify data persisted
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "SELECT * FROM tx_tbl WHERE name = 'Committed'"}
                                            session-id))
            body (parse-body res)]
        (is (= 1 (count (get body "rows")))))

      ;; Cleanup
      (test-handler (json-request :post "/queries" {"sql" "DROP TABLE tx_tbl"} session-id))
      (test-handler (json-request :delete "/sessions" nil session-id))))

  (testing "Transaction rollback"
    (let [response (test-handler (json-request :post "/sessions" {"database" "tx_rollback_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Create table
      (test-handler (json-request :post "/queries"
                                  {"sql" "CREATE TABLE IF NOT EXISTS tx_rb_tbl (id INTEGER PRIMARY KEY, name TEXT)"}
                                  session-id))

      ;; Insert initial data (committed)
      (test-handler (json-request :post "/queries"
                                  {"sql" "INSERT INTO tx_rb_tbl (name) VALUES ('Initial')"}
                                  session-id))

      ;; Begin transaction
      (test-handler (json-request :post "/transactions/begin" nil session-id))

      ;; Insert data in transaction
      (test-handler (json-request :post "/queries"
                                  {"sql" "INSERT INTO tx_rb_tbl (name) VALUES ('ToBeRolledBack')"}
                                  session-id))

      ;; Rollback transaction
      (let [rollback-res (test-handler (json-request :post "/transactions/rollback" nil session-id))
            rollback-body (parse-body rollback-res)]
        (is (= 200 (:status rollback-res)))
        (is (= "ROLLED_BACK" (get rollback-body "status"))))

      ;; Verify rolled back data is not present
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "SELECT * FROM tx_rb_tbl WHERE name = 'ToBeRolledBack'"}
                                            session-id))
            body (parse-body res)]
        (is (= 0 (count (get body "rows")))))

      ;; Verify initial data is still present
      (let [res (test-handler (json-request :post "/queries"
                                            {"sql" "SELECT * FROM tx_rb_tbl WHERE name = 'Initial'"}
                                            session-id))
            body (parse-body res)]
        (is (= 1 (count (get body "rows")))))

      ;; Cleanup
      (test-handler (json-request :post "/queries" {"sql" "DROP TABLE tx_rb_tbl"} session-id))
      (test-handler (json-request :delete "/sessions" nil session-id))))

  (testing "Transaction error cases"
    (let [response (test-handler (json-request :post "/sessions" {"database" "tx_error_test"} nil))
          session-id (get (parse-body response) "session-id")]

      ;; Commit without transaction should fail
      (let [res (test-handler (json-request :post "/transactions/commit" nil session-id))
            body (parse-body res)]
        (is (= 400 (:status res)))
        (is (some? (get body "error"))))

      ;; Rollback without transaction should fail
      (let [res (test-handler (json-request :post "/transactions/rollback" nil session-id))
            body (parse-body res)]
        (is (= 400 (:status res)))
        (is (some? (get body "error"))))

      ;; Begin twice should fail
      (test-handler (json-request :post "/transactions/begin" nil session-id))
      (let [res (test-handler (json-request :post "/transactions/begin" nil session-id))
            body (parse-body res)]
        (is (= 400 (:status res)))
        (is (some? (get body "error"))))

      ;; Cleanup
      (test-handler (json-request :post "/transactions/rollback" nil session-id))
      (test-handler (json-request :delete "/sessions" nil session-id)))))
