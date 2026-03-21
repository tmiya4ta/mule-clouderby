(ns clouderby.server
  "clouderby protocol server - Reference implementation with SQLite"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [reitit.ring :as ring]
            [next.jdbc :as jdbc])
  (:import [java.util UUID]
           [java.time Instant])
  (:gen-class))

;; ==================== Session Management ====================

(defonce sessions (atom {}))

(defn- create-session [database data-dir]
  (let [session-id (str (UUID/randomUUID))
        db-file (str data-dir "/" database ".db")
        datasource (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (swap! sessions assoc session-id {:datasource datasource
                                       :database database
                                       :statements {}
                                       :transaction nil  ;; holds {:conn <Connection>} when in transaction
                                       :created-at (Instant/now)})
    session-id))

(defn- get-session [session-id]
  (get @sessions session-id))

(defn- close-session [session-id]
  ;; Close any open transaction connection
  (when-let [session (get-session session-id)]
    (when-let [tx (:transaction session)]
      (try
        (.rollback (:conn tx))
        (.close (:conn tx))
        (catch Exception _))))
  (swap! sessions dissoc session-id))

(defn- get-session-id [request]
  (get-in request [:headers "x-clouderby-session-id"]))

;; ==================== Transaction Management ====================

(defn- begin-transaction [session-id]
  (let [session (get-session session-id)]
    (if (:transaction session)
      {:error "Transaction already in progress"}
      (let [conn (jdbc/get-connection (:datasource session))]
        (.setAutoCommit conn false)
        (swap! sessions assoc-in [session-id :transaction] {:conn conn})
        {:status "STARTED" :in-transaction true}))))

(defn- commit-transaction [session-id]
  (let [session (get-session session-id)]
    (if-let [tx (:transaction session)]
      (do
        (.commit (:conn tx))
        (.close (:conn tx))
        (swap! sessions assoc-in [session-id :transaction] nil)
        {:status "COMMITTED" :in-transaction false})
      {:error "No transaction in progress"})))

(defn- rollback-transaction [session-id]
  (let [session (get-session session-id)]
    (if-let [tx (:transaction session)]
      (do
        (.rollback (:conn tx))
        (.close (:conn tx))
        (swap! sessions assoc-in [session-id :transaction] nil)
        {:status "ROLLED_BACK" :in-transaction false})
      {:error "No transaction in progress"})))

;; ==================== Statement Management ====================

(defn- create-statement [session-id sql]
  (let [stmt-id (str (UUID/randomUUID))
        param-count (count (re-seq #"\?" sql))]
    (swap! sessions update-in [session-id :statements] assoc stmt-id {:sql sql :param-count param-count})
    {:statement-id stmt-id :param-count param-count}))

(defn- get-statement [session-id stmt-id]
  (get-in @sessions [session-id :statements stmt-id]))

(defn- close-statement [session-id stmt-id]
  (swap! sessions update-in [session-id :statements] dissoc stmt-id))

;; ==================== SQL Execution ====================

(defn- sqlite-type [type-name]
  (case (some-> type-name str .toUpperCase)
    "INTEGER" "INTEGER"
    "INT" "INTEGER"
    "BIGINT" "INTEGER"
    "REAL" "REAL"
    "FLOAT" "REAL"
    "DOUBLE" "REAL"
    "BLOB" "BLOB"
    "TEXT"))

(defn- result-set->columns [rs-meta]
  (let [col-count (.getColumnCount rs-meta)]
    (mapv (fn [i]
            {:name (.getColumnName rs-meta i)
             :type (sqlite-type (.getColumnTypeName rs-meta i))
             :table-name (.getTableName rs-meta i)
             :nullable (.isNullable rs-meta i)})
          (range 1 (inc col-count)))))

(defn- collect-rows [rs col-count fetch-size]
  (loop [rows [] cnt 0]
    (if (and (.next rs) (< cnt fetch-size))
      (let [row (mapv #(.getObject rs (inc %)) (range col-count))]
        (recur (conj rows row) (inc cnt)))
      {:rows rows :has-more (and (>= cnt fetch-size) (.next rs))})))

(defn- execute-query-on-conn [conn sql fetch-size]
  (let [stmt (.createStatement conn)
        rs (.executeQuery stmt sql)
        rs-meta (.getMetaData rs)
        columns (result-set->columns rs-meta)
        col-count (count columns)
        {:keys [rows has-more]} (collect-rows rs col-count fetch-size)]
    {:columns columns
     :rows rows
     :done (not has-more)}))

(defn- execute-update-on-conn [conn sql]
  (let [stmt (.createStatement conn)]
    (.execute stmt sql)
    (let [update-count (.getUpdateCount stmt)
          rs (.getGeneratedKeys stmt)
          last-id (when (.next rs) (.getLong rs 1))]
      {:update-count (max 0 update-count)
       :last-insert-id (or last-id 0)})))

(defn- execute-query [datasource sql fetch-size]
  (with-open [conn (jdbc/get-connection datasource)]
    (execute-query-on-conn conn sql fetch-size)))

(defn- execute-update [datasource sql]
  (with-open [conn (jdbc/get-connection datasource)]
    (execute-update-on-conn conn sql)))

(defn- is-query? [sql]
  (let [trimmed (-> sql str .trim .toUpperCase)]
    (or (.startsWith trimmed "SELECT")
        (.startsWith trimmed "PRAGMA")
        (.startsWith trimmed "EXPLAIN"))))

(defn- execute-sql [session sql fetch-size]
  (if-let [tx (:transaction session)]
    ;; Use transaction connection
    (if (is-query? sql)
      (execute-query-on-conn (:conn tx) sql fetch-size)
      (execute-update-on-conn (:conn tx) sql))
    ;; Use auto-commit connection
    (if (is-query? sql)
      (execute-query (:datasource session) sql fetch-size)
      (execute-update (:datasource session) sql))))

(defn- params->values [params]
  (mapv (fn [p]
          (let [v (get p "value")]
            (case (get p "type")
              "NULL" nil
              "INTEGER" (long v)
              "REAL" (double v)
              "BLOB" (.decode (java.util.Base64/getDecoder) ^String v)
              v)))
        (sort-by #(get % "index") params)))

(defn- execute-prepared-on-conn [conn sql params query?]
  (let [values (params->values params)
        ps (.prepareStatement conn sql)]
    ;; Set parameters
    (doseq [[idx val] (map-indexed vector values)]
      (if (nil? val)
        (.setNull ps (inc idx) java.sql.Types/NULL)
        (.setObject ps (inc idx) val)))
    (if query?
      (let [rs (.executeQuery ps)
            rs-meta (.getMetaData rs)
            columns (result-set->columns rs-meta)
            col-count (count columns)
            {:keys [rows]} (collect-rows rs col-count 1000)]
        {:columns columns :rows rows :done true})
      (let [cnt (.executeUpdate ps)]
        {:update-count cnt}))))

(defn- execute-prepared [session sql params query?]
  (if-let [tx (:transaction session)]
    ;; Use transaction connection
    (execute-prepared-on-conn (:conn tx) sql params query?)
    ;; Use auto-commit connection
    (with-open [conn (jdbc/get-connection (:datasource session))]
      (execute-prepared-on-conn conn sql params query?))))

;; ==================== Handlers ====================

(defn health-handler [_]
  {:status 200
   :body {:status "UP"
          :service "clouderby-clj-server"
          :timestamp (str (Instant/now))}})

(defn create-session-handler [{:keys [body] :as req}]
  (let [database (get body "database" "default")
        data-dir (or (System/getenv "CLOUDERBY_DATA_DIR") "./data")]
    ;; Ensure data directory exists
    (.mkdirs (java.io.File. data-dir))
    (let [session-id (create-session database data-dir)]
      {:status 200
       :body {:session-id session-id
              :server-version "1.0.0"}})))

(defn delete-session-handler [req]
  (if-let [session-id (get-session-id req)]
    (do (close-session session-id)
        {:status 200 :body {:closed true}})
    {:status 404 :body {:error "Session not found"}}))

(defn execute-query-handler [{:keys [body] :as req}]
  (if-let [session-id (get-session-id req)]
    (if-let [session (get-session session-id)]
      (try
        (let [sql (get body "sql")
              fetch-size (get body "fetch-size" 100)
              result (execute-sql session sql fetch-size)]
          {:status 200 :body result})
        (catch Exception e
          {:status 500 :body {:error (.getMessage e)}}))
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn create-statement-handler [{:keys [body] :as req}]
  (if-let [session-id (get-session-id req)]
    (if (get-session session-id)
      (let [sql (get body "sql")
            result (create-statement session-id sql)]
        {:status 200 :body result})
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn execute-statement-handler [{{:keys [statement-id]} :path-params :keys [body] :as req}]
  (if-let [session-id (get-session-id req)]
    (if-let [session (get-session session-id)]
      (if-let [stmt (get-statement session-id statement-id)]
        (try
          (let [params (get body "params" [])
                query? (get body "query" false)
                result (execute-prepared session (:sql stmt) params query?)]
            {:status 200 :body result})
          (catch Exception e
            {:status 500 :body {:error (.getMessage e)}}))
        {:status 404 :body {:error "Statement not found"}})
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn delete-statement-handler [{{:keys [statement-id]} :path-params :as req}]
  (if-let [session-id (get-session-id req)]
    (do (close-statement session-id statement-id)
        {:status 200 :body {:closed true}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn batch-statement-handler [{{:keys [statement-id]} :path-params :keys [body] :as req}]
  (if-let [session-id (get-session-id req)]
    (if-let [session (get-session session-id)]
      (if-let [stmt (get-statement session-id statement-id)]
        (try
          (let [param-sets (get body "param-sets" [])
                results (mapv #(execute-prepared session (:sql stmt) % false) param-sets)
                update-counts (mapv :update-count results)]
            {:status 200 :body {:update-counts update-counts}})
          (catch Exception e
            {:status 500 :body {:error (.getMessage e)}}))
        {:status 404 :body {:error "Statement not found"}})
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn metadata-info-handler [req]
  (if-let [session-id (get-session-id req)]
    (if (get-session session-id)
      {:status 200
       :body {:product-name "clouderby-clj-server"
              :product-version "1.0.0"
              :driver-name "clouderby JDBC Driver"
              :driver-version "1.0.0"
              :identifier-quote-string "\""
              :catalog-separator "."
              :catalog-term "database"
              :schema-term "schema"}}
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn metadata-tables-handler [req]
  (if-let [session-id (get-session-id req)]
    (if-let [session (get-session session-id)]
      (try
        (let [result (execute-sql session
                                  "SELECT name, type FROM sqlite_master WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%' ORDER BY name"
                                  1000)
              tables (mapv (fn [[name type]]
                            {:name name :type (if (= type "table") "TABLE" "VIEW")})
                          (:rows result))]
          {:status 200 :body {:tables tables}})
        (catch Exception e
          {:status 500 :body {:error (.getMessage e)}}))
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

;; Transaction handlers

(defn begin-transaction-handler [req]
  (if-let [session-id (get-session-id req)]
    (if (get-session session-id)
      (let [result (begin-transaction session-id)]
        (if (:error result)
          {:status 400 :body result}
          {:status 200 :body result}))
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn commit-transaction-handler [req]
  (if-let [session-id (get-session-id req)]
    (if (get-session session-id)
      (try
        (let [result (commit-transaction session-id)]
          (if (:error result)
            {:status 400 :body result}
            {:status 200 :body result}))
        (catch Exception e
          {:status 500 :body {:error (.getMessage e)}}))
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

(defn rollback-transaction-handler [req]
  (if-let [session-id (get-session-id req)]
    (if (get-session session-id)
      (try
        (let [result (rollback-transaction session-id)]
          (if (:error result)
            {:status 400 :body result}
            {:status 200 :body result}))
        (catch Exception e
          {:status 500 :body {:error (.getMessage e)}}))
      {:status 404 :body {:error "Session not found"}})
    {:status 400 :body {:error "Missing X-Clouderby-Session-Id header"}}))

;; ==================== Router ====================

(def router
  (ring/router
   [["/health" {:get health-handler}]
    ["/sessions" {:post create-session-handler
                  :delete delete-session-handler}]
    ["/queries" {:post execute-query-handler}]
    ["/statements" {:post create-statement-handler}]
    ["/statements/:statement-id" {:delete delete-statement-handler}]
    ["/statements/:statement-id/execute" {:post execute-statement-handler}]
    ["/statements/:statement-id/batch" {:post batch-statement-handler}]
    ["/transactions/begin" {:post begin-transaction-handler}]
    ["/transactions/commit" {:post commit-transaction-handler}]
    ["/transactions/rollback" {:post rollback-transaction-handler}]
    ["/metadata/info" {:get metadata-info-handler}]
    ["/metadata/tables" {:get metadata-tables-handler}]]))

(def app
  (ring/ring-handler router (ring/create-default-handler)))

(def handler
  (-> app
      (wrap-json-body {:keywords? false})
      wrap-json-response))

;; ==================== Main ====================

(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8081"))
        data-dir (or (System/getenv "CLOUDERBY_DATA_DIR") "./data")]
    ;; Ensure data directory exists
    (.mkdirs (java.io.File. data-dir))

    (println "========================================")
    (println "clouderby Protocol Server (Clojure)")
    (println "========================================")
    (println (str "Port:     " port))
    (println (str "Data dir: " data-dir))
    (println "========================================")

    (jetty/run-jetty handler {:port port :join? true})))
