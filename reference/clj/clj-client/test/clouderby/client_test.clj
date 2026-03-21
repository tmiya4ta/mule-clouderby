(ns clouderby.client-test
  (:require [clojure.test :refer :all]
            [clouderby.client :as client]))

;; These tests require a running clouderby server
;; Set CLOUDERBY_TEST_URL environment variable or use default localhost:8081

(def ^:dynamic *test-url* (or (System/getenv "CLOUDERBY_TEST_URL") "http://localhost:8081"))

(defn with-server-check [f]
  (try
    (let [health (client/health *test-url*)]
      (if (= 200 (:status health))
        (f)
        (println "SKIP: Server not available at" *test-url*)))
    (catch Exception e
      (println "SKIP: Server not available at" *test-url* "-" (.getMessage e)))))

(use-fixtures :once with-server-check)

(deftest health-test
  (testing "Health check"
    (let [{:keys [status body]} (client/health *test-url*)]
      (is (= 200 status))
      (is (= "UP" (get body "status"))))))

(deftest connection-test
  (testing "Connect and disconnect"
    (let [conn (client/connect *test-url* "conn_test")]
      (is (some? (:session-id conn)))
      (is (some? (:client conn)))
      (client/disconnect conn))))

(deftest execute-test
  (testing "Execute SQL statements"
    (let [conn (client/connect *test-url* "exec_test")]
      (try
        ;; Create table
        (let [result (client/execute conn "CREATE TABLE exec_tbl (id INTEGER PRIMARY KEY, name TEXT)")]
          (is (contains? result "update-count")))

        ;; Insert
        (let [result (client/execute conn "INSERT INTO exec_tbl (name) VALUES ('Test')")]
          (is (= 1 (get result "update-count"))))

        ;; Select
        (let [result (client/execute conn "SELECT * FROM exec_tbl")]
          (is (= 1 (count (get result "rows"))))
          (is (some? (get result "columns"))))

        ;; Cleanup
        (client/execute conn "DROP TABLE exec_tbl")

        (finally
          (client/disconnect conn))))))

(deftest prepared-statement-test
  (testing "Prepared statement"
    (let [conn (client/connect *test-url* "prep_test")]
      (try
        ;; Create table
        (client/execute conn "CREATE TABLE prep_tbl (id INTEGER PRIMARY KEY, name TEXT, value REAL)")

        ;; Prepare statement
        (let [stmt (client/prepare conn "INSERT INTO prep_tbl (name, value) VALUES (?, ?)")]
          (is (some? (:statement-id stmt)))
          (is (= 2 (:param-count stmt)))

          ;; Execute with params
          (let [result (client/execute-prepared stmt
                                                [{"index" 1 "type" "TEXT" "value" "Alice"}
                                                 {"index" 2 "type" "REAL" "value" 1.23}])]
            (is (= 1 (get result "update-count"))))

          ;; Close statement
          (client/close-statement stmt))

        ;; Verify
        (let [result (client/execute conn "SELECT * FROM prep_tbl")]
          (is (= 1 (count (get result "rows")))))

        ;; Cleanup
        (client/execute conn "DROP TABLE prep_tbl")

        (finally
          (client/disconnect conn))))))

(deftest query-with-results-test
  (testing "Query with multiple rows"
    (let [conn (client/connect *test-url* "query_test")]
      (try
        ;; Setup
        (client/execute conn "CREATE TABLE query_tbl (id INTEGER PRIMARY KEY, val INTEGER)")
        (client/execute conn "INSERT INTO query_tbl (val) VALUES (10), (20), (30)")

        ;; Query
        (let [result (client/execute conn "SELECT * FROM query_tbl ORDER BY id")]
          (is (= 3 (count (get result "rows"))))
          (is (= 2 (count (get result "columns")))))

        ;; Query with condition
        (let [result (client/execute conn "SELECT val FROM query_tbl WHERE val > 15")]
          (is (= 2 (count (get result "rows")))))

        ;; Cleanup
        (client/execute conn "DROP TABLE query_tbl")

        (finally
          (client/disconnect conn))))))

(deftest error-handling-test
  (testing "SQL error handling"
    (let [conn (client/connect *test-url* "error_test")]
      (try
        (is (thrown-with-msg? Exception #"Execute failed"
                              (client/execute conn "INVALID SQL SYNTAX")))
        (finally
          (client/disconnect conn))))))

(deftest print-result-test
  (testing "print-result utility"
    ;; Query result
    (let [result {"columns" [{"name" "id"} {"name" "name"}]
                  "rows" [[1 "Alice"] [2 "Bob"]]}]
      (is (string? (with-out-str (client/print-result result)))))

    ;; Update result
    (let [result {"update-count" 5}]
      (is (string? (with-out-str (client/print-result result)))))

    ;; DDL result
    (let [result {"update-count" 0}]
      (is (string? (with-out-str (client/print-result result)))))))
