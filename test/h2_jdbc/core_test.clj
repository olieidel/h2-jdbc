(ns h2-jdbc.core-test
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [h2-jdbc.core :refer :all])
  (:import java.sql.Time
           [java.time LocalDate LocalDateTime LocalTime OffsetDateTime ZoneOffset]
           [java.util Arrays UUID]))


;; -- Fixtures -----------------------------------------------------------------

(def ^:private db-spec
  "The database spec, needed for connecting to the H2 DB. Let's use an
  in-memory DB for our purposes here."
  {:classname   "org.h2.Driver"
   ;; use an in-memory db
   :subprotocol "h2:mem"
   ;; set `DB_CLOSE_DELAY`, otherwise it disappears after each query
   :subname     "test_db;DB_CLOSE_DELAY=-1"
   :user        "sa"
   :password    ""})

(defn- cleanup-db!
  "Remove everything (all tables) from the db."
  []
  (jdbc/execute! db-spec "DROP ALL OBJECTS"))

(defn- with-db
  "A fixture to delete everything from our in-memory test db before and
  after running each test."
  [f]
  ;; cleanup everything before running a test
  (cleanup-db!)
  ;; run the test
  (f)
  ;; cleanup everything after running a test
  (cleanup-db!))

;; use this fixture with every (`:each`) test
(use-fixtures :each with-db)


;; -- Data type test helpers ---------------------------------------------------

(def ^:private datatype-tests
  "We define our tests as data. For each test, we create a database with
  one column of a specific data type. Then, we insert some values into
  it. Finally, we query those values and compare them with the
  original (inserted) ones.

  Each test is a map with these keys:

  `:dtype`: The H2 data type as in a SQL statement

  `:vals`: A vector of values to insert and then read back from the
  database. We'll check whether the each read value from the db is
  equal to the inserted value.

  `:compare-fn` (optional): A function used to compare the inserted
  and read value. It should take two arguments. If not provided, `=`
  is used."
  [{:dtype "INT"
    :vals  [-2147483648 0 2147483647]}
   {:dtype "BOOLEAN"
    :vals  [true false]}
   {:dtype "TINYINT"
    :vals  [-128 0 127]}
   {:dtype "SMALLINT"
    :vals  [-32768 0 32767]}
   {:dtype "BIGINT"
    :vals [-9223372036854775808 0 9223372036854775807]}
   ;; TODO: decimal
   {:dtype "DOUBLE"
    :vals [0.1 1.0]}
   {:dtype "REAL"
    :vals [(float 0.1) (float 1.0)]}
   {:dtype "TIME"
    ;; ugh, nanos are ignored! setting to zero.
    :vals [(LocalTime/of 23 50 10 0)]}
   {:dtype "DATE"
    :vals [(LocalDate/of 2018 12 30)]}
   {:dtype "TIMESTAMP"
    :vals [(LocalDateTime/of 2018 05 30 23 50 10)]}
   {:dtype "TIMESTAMP WITH TIME ZONE"
    :vals [(OffsetDateTime/of (LocalDate/of 2018 05 30)
                              (LocalTime/of 23 50 10)
                              (ZoneOffset/ofHours 2))]}
   {:dtype "BINARY"
    :vals [(byte-array [(byte 0x43) (byte 0x6c) (byte 0x6f) (byte 0x6a)
                        (byte 0x75) (byte 0x72) (byte 0x65) (byte 0x21)])]
    :compare-fn #(Arrays/equals %1 %2)}
   {:dtype "VARCHAR"
    :vals ["hello" "world"]}
   {:dtype "VARCHAR_IGNORECASE"
    :vals ["hello" "world"]}
   {:dtype "CHAR"
    :vals ["h" "w"]}
   ;; skip: BLOB
   ;; skip: CLOB
   {:dtype "UUID"
    :vals [(UUID/randomUUID)]}
   {:dtype "ARRAY"
    :vals [(into-array [1 2 3])]
    :compare-fn #(Arrays/equals %1 %2)}
   ;; skip: enum
   ;; skip: geometry
   ])

(defn- safe-str
  "Remove brackets and spaces from a \"data type\" string so we can
  create a SQL table containing its name."
  [s]
  (-> s
      (str/lower-case)
      (str/replace #"\(|\)|(\s+)" "_")))

(defn- test-datatype!
  "Takes an entry of the `datatype-tests` vector above.

  1. Create a table with one column where the column has the specific
  data type.

  2. Insert each value specific in `:vals`. Assert that insertion
  succeeded.

  3. Query each value from the db and assert whether it's equal based
  on `=` or `:compare-fn`, if provided."
  [{:keys [dtype vals compare-fn]}]
  (let [field-name (safe-str dtype)
        table-name (str field-name "_table")
        create-table-stmt (str "CREATE TABLE " table-name
                               " (" field-name " " dtype ")")]
    (jdbc/execute! db-spec create-table-stmt)

    (doseq [val vals]
      (is (= '(nil)
             (jdbc/insert! db-spec table-name
                           (assoc {} (keyword field-name) val))))
      (let [val-from-db (-> (jdbc/query db-spec
                                        (str "SELECT * FROM " table-name))
                            first
                            (get (keyword field-name)))
            compare-fn' (if compare-fn compare-fn =)]
        (compare-fn' val val-from-db))
      (jdbc/delete! db-spec table-name []))))


;; -- Tests --------------------------------------------------------------------

(deftest datatypes-test
  (testing "Generated data type tests"
    (doseq [datatype-test datatype-tests]
      (test-datatype! datatype-test))))


(comment
  (create-table-with-all-h2-datatypes!)
  (jdbc/query db-spec "SHOW TABLES")
  (jdbc/execute! db-spec "CREATE TABLE int_table (int INT)")
  (jdbc/insert! db-spec "int_table" {:int 1})
  (jdbc/delete! db-spec "int_table" [])
  (jdbc/query db-spec "SELECT * FROM int_table")

  (jdbc/execute! db-spec "CREATE TABLE real_table (real REAL)")
  (jdbc/insert! db-spec "real_table" {:real (float 0.1)})
  (->> (jdbc/query db-spec "SELECT * FROM real_table")
       first
       :real
       (= (float 0.1)))
  (jdbc/delete! db-spec "real_table" [])

  (jdbc/execute! db-spec "CREATE TABLE date_table (date DATE)")
  (jdbc/insert! db-spec "date_table" {:date (LocalTime/of 12 5 10 5)})
  (-> (jdbc/query db-spec "SELECT * FROM date_table")
      first :date type)

  (jdbc/execute! db-spec
                 "CREATE TABLE timestamp_table (timestamp TIMESTAMP WITH TIME ZONE)")
;; => [0]
  (jdbc/insert! db-spec "timestamp_table"
                {:timestamp (OffsetDateTime/now)})
  (-> (jdbc/query db-spec "SELECT * FROM timestamp_table")
      first :timestamp)

  (jdbc/execute! db-spec
                 "CREATE TABLE enum_table (enum ENUM('foo', 'bar', 'baz'))")
  (jdbc/insert! db-spec "enum_table"
                {:enum "foo"})
  (-> (jdbc/query db-spec "SELECT * FROM enum_table")
      first :blob)

  (OffsetDateTime/of 2018 05 28 23 50 10 99 (ZoneOffset/ofTotalSeconds 0))

  (jdbc/execute! db-spec "DROP ALL OBJECTS")

  (Arrays/equals
   (byte-array [(byte 0x43) (byte 0x6c) (byte 0x6f) (byte 0x6a)
                (byte 0x75) (byte 0x72) (byte 0x65) (byte 0x21)])
   (byte-array [(byte 0x43) (byte 0x6c) (byte 0x6f) (byte 0x6a)
                (byte 0x75) (byte 0x72) (byte 0x65) (byte 0x21)])))
