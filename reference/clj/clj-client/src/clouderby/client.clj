(ns clouderby.client
  "clouderby protocol client - Connects to any clouderby protocol server"
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]
           [com.fasterxml.jackson.databind ObjectMapper DeserializationFeature]))

(def ^:private timeout (Duration/ofSeconds 30))

(def ^:private object-mapper
  (doto (ObjectMapper.)
    (.configure DeserializationFeature/FAIL_ON_UNKNOWN_PROPERTIES false)))

(defn- create-http-client []
  (-> (HttpClient/newBuilder)
      (.connectTimeout timeout)
      (.build)))

(defn- to-json [data]
  (.writeValueAsString object-mapper data))

(defn- from-json [json-str]
  (.readValue object-mapper json-str java.util.Map))

(defn- request
  "HTTP request to clouderby server"
  [^HttpClient client method base-url path body session-id]
  (let [uri (URI/create (str base-url path))
        builder (-> (HttpRequest/newBuilder)
                    (.uri uri)
                    (.timeout timeout)
                    (.header "Content-Type" "application/json"))]
    (when session-id
      (.header builder "X-Clouderby-Session-Id" session-id))
    (case method
      :get (.GET builder)
      :post (.POST builder (HttpRequest$BodyPublishers/ofString (to-json body)))
      :delete (.DELETE builder))
    (let [http-request (.build builder)
          response (.send client http-request (HttpResponse$BodyHandlers/ofString))
          status (.statusCode response)
          body-str (.body response)]
      {:status status
       :body (when (seq body-str) (from-json body-str))})))

;; ==================== Public API ====================

(defn health
  "Server health check"
  [base-url]
  (let [client (create-http-client)]
    (request client :get base-url "/health" nil nil)))

(defn connect
  "Create a session on clouderby server"
  [base-url database]
  (let [client (create-http-client)
        response (request client :post base-url "/sessions"
                          {"database" database} nil)
        body (:body response)]
    (if (get body "error")
      (throw (ex-info (str "Connection failed: " (get body "error")) {:response response}))
      {:client client
       :base-url base-url
       :session-id (get body "session-id")
       :database database})))

(defn disconnect
  "Close the session"
  [conn]
  (request (:client conn) :delete (:base-url conn) "/sessions"
           nil (:session-id conn)))

(defn execute
  "Execute SQL (SELECT/INSERT/UPDATE/DELETE/DDL)"
  [conn sql & {:keys [fetch-size] :or {fetch-size 1000}}]
  (let [response (request (:client conn) :post (:base-url conn) "/queries"
                          {"sql" sql "fetch-size" fetch-size}
                          (:session-id conn))
        body (:body response)]
    (if (get body "error")
      (throw (ex-info (str "Execute failed: " (get body "error")) {:sql sql :response response}))
      body)))

(defn prepare
  "Create a prepared statement"
  [conn sql]
  (let [response (request (:client conn) :post (:base-url conn) "/statements"
                          {"sql" sql}
                          (:session-id conn))
        body (:body response)]
    (if (get body "error")
      (throw (ex-info (str "Prepare failed: " (get body "error")) {:sql sql :response response}))
      {:conn conn
       :statement-id (get body "statement-id")
       :param-count (get body "param-count")})))

(defn execute-prepared
  "Execute a prepared statement with parameters"
  [stmt params & {:keys [query?] :or {query? false}}]
  (let [conn (:conn stmt)
        path (str "/statements/" (:statement-id stmt) "/execute")
        response (request (:client conn) :post (:base-url conn) path
                          {"params" params "query" query?}
                          (:session-id conn))
        body (:body response)]
    (if (get body "error")
      (throw (ex-info (str "Execute prepared failed: " (get body "error")) {:response response}))
      body)))

(defn close-statement
  "Close a prepared statement"
  [stmt]
  (let [conn (:conn stmt)
        path (str "/statements/" (:statement-id stmt))]
    (request (:client conn) :delete (:base-url conn) path
             nil (:session-id conn))))

;; ==================== Utility Functions ====================

(defn print-result
  "Pretty print query result"
  [result]
  (cond
    ;; Query result (has columns and rows)
    (get result "columns")
    (let [columns (get result "columns")
          rows (get result "rows")
          col-names (mapv #(get % "name") columns)]
      (println "\n=== Query Result ===")
      (println (str/join " | " col-names))
      (println (str/join "" (repeat (+ (* 3 (count col-names)) (reduce + (map count col-names))) "-")))
      (doseq [row rows]
        (println (str/join " | " (map str row))))
      (println (str "\nTotal rows: " (count rows))))

    ;; Update result
    (contains? result "update-count")
    (let [cnt (get result "update-count")
          last-id (get result "last-insert-id")]
      (if (> cnt 0)
        (println (str "\n=== Update Result ===\nRows affected: " cnt
                      (when (and last-id (> last-id 0))
                        (str ", Last insert ID: " last-id))))
        (println "\n=== DDL executed successfully ===")))

    :else
    (println (str "\n=== Result ===\n" result))))

;; ==================== Demo ====================

(defn run-demo
  "Demo: connect to server and run queries"
  [base-url]
  (println "========================================")
  (println "clouderby Protocol Client Demo")
  (println "========================================")
  (println (str "\nTarget: " base-url))

  ;; Health check
  (println "\n[1] Health Check...")
  (let [{:keys [status body]} (health base-url)]
    (println (str "    Status: " status))
    (println (str "    Service: " (get body "service"))))

  ;; Connect
  (println "\n[2] Creating session...")
  (let [conn (connect base-url "demo")]
    (println (str "    Session ID: " (:session-id conn)))

    (try
      ;; Create table
      (println "\n[3] Creating test table...")
      (execute conn "DROP TABLE IF EXISTS clj_demo")
      (print-result (execute conn "CREATE TABLE clj_demo (id INTEGER PRIMARY KEY, name TEXT, value REAL)"))

      ;; Insert with prepared statement
      (println "\n[4] Inserting with prepared statement...")
      (let [stmt (prepare conn "INSERT INTO clj_demo (name, value) VALUES (?, ?)")]
        (println (str "    Statement ID: " (:statement-id stmt)))
        (execute-prepared stmt [{"index" 1 "type" "TEXT" "value" "Alice"}
                                {"index" 2 "type" "REAL" "value" 3.14}])
        (execute-prepared stmt [{"index" 1 "type" "TEXT" "value" "Bob"}
                                {"index" 2 "type" "REAL" "value" 2.71}])
        (close-statement stmt)
        (println "    Inserted 2 rows"))

      ;; Query
      (println "\n[5] Querying data...")
      (print-result (execute conn "SELECT * FROM clj_demo ORDER BY id"))

      ;; Cleanup
      (println "\n[6] Cleanup...")
      (execute conn "DROP TABLE clj_demo")
      (println "    Table dropped.")

      (finally
        (println "\n[7] Closing session...")
        (disconnect conn)
        (println "    Done.")))

    (println "\n========================================")))

(defn -main [& args]
  (let [url (or (first args) "http://localhost:8081")]
    (try
      (run-demo url)
      (System/exit 0)
      (catch Exception e
        (println (str "\nError: " (.getMessage e)))
        (.printStackTrace e)
        (System/exit 1)))))
