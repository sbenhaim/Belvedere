(ns Scyan.pg
  (:require [next.jdbc :as j]
            [next.jdbc.sql :as sql]
            [next.jdbc.sql.builder :as builder]
            [next.jdbc.prepare :as prep]
            [clojure.string :as str]
            [jsonista.core :as json]
            [Scyan.openai :as openai]
            [clojure.core.match :refer [match]]
            [Scyan.docs :as docs])
  (:import [com.pgvector PGvector]
           [org.postgresql.util PGobject]))


(def db-conf {:dbtype "postgresql"
              :dbname "scyan"
              :user "postgres"
              :password "postgres"
              :host "localhost"
              :port 5432})

(def conn (atom nil))


(defn connect!
  "Connects to the PostgreSQL database."
  []
  (reset! conn (j/get-connection db-conf)))


(defn vec->pgvec
  "Converts a Clojure vector to a PostgreSQL array representation.
  - vec: The Clojure vector to convert."
  [vec]
  (str "[" (str/join "," vec) "]"))


(extend-protocol prep/SettableParameter

  clojure.lang.IPersistentVector
  (set-parameter [^clojure.lang.PersistentVector v ^java.sql.PreparedStatement stmt ^long i]
    (prep/set-parameter (PGvector. (into-array Float/TYPE v)) stmt i))

  clojure.lang.IPersistentMap
  (set-parameter [^clojure.lang.PersistentArrayMap m ^java.sql.PreparedStatement stmt ^long i]
    (.setObject stmt i (doto (PGobject.)
                         (.setType "json")
                         (.setValue (json/write-value-as-string m))))))


((juxt :one :two) {:one 1 :two 2})

(defn insert-docs!
  "Inserts a batch of documents into the database."
  [rows collection]
  (let [keys [:contents :embedding :meta :collection]
        data (map #(assoc % :collection collection) rows)
        data (map (apply juxt keys) data)
        suffix "on conflict (collection, contents)
do update set embedding = excluded.embedding, meta = excluded.meta"]
    (with-open [conn (j/get-connection db-conf)]
      (j/execute!
       conn
       (builder/for-insert-multi :doc keys data {:suffix suffix})))
    true))



(comment

  (insert-docs! [{:contents "Hello, Selah!"
                  :embedding (openai/embedding "Hello, Selah!")
                  :meta {:over 3 :under 5}}]
                "test")
  )


(defn search-docs
  [col query-vec k]
  (with-open [conn (j/get-connection db-conf)]
    (sql/query conn ["select * from search(?, ?, ?)"
                     col query-vec (int k)])))



(defrecord PostgresIndex [collection]
  Scyan.docs.Index
  (is-empty? [_]
    (with-open [conn (j/get-connection db-conf)]
      (match
       (sql/query conn ["select count(*) from doc where collection = ?" collection])
       [{:count n}] (zero? n))))
  (top-n [_ query-vec k]
    (with-open [conn (j/get-connection db-conf)]
      (sql/query conn ["select * from search(?, ?, ?)"
                       collection query-vec (int k)])))
  (insert! [_ doc]
    (insert-docs! [doc] collection))
  (insert-many! [_ docs]
    (insert-docs! docs collection))
  (save! [_] #_:noop))




(with-open [conn (j/get-connection db-conf)]
  (j/execute! conn
              (builder/for-insert-multi :doc
                                        [:collection :contents :embedding :meta]
                                        [["test" "test" [1 2 3] {:a :b}]]
                                        {:suffix "on conflict (collection, contents)
do update set embedding = excluded.embedding, meta = excluded.meta"})))


(insert-docs! [{:contents "test" :embedding [1 2 3]}]
              "test")


(comment

(with-open [conn (j/get-connection db-conf)]
  (match
   (sql/query conn ["select count(*) from doc where collection = ?" "test"])
   [{:count n}] n))

(let [idx (->PostgresIndex "text")]
  (docs/is-empty? idx))

  (def emb (openai/embedding "Hello, Sel-Bell!"))
  (search-docs "test" emb 1)
  )


(comment
  (connect!)


  (sql/query @conn ["select collection, text from doc where collection = ?" "test"])

  (let [text "Stuff"
        vect (openai/embedding text)]
    #_(insert-docs! [{:collection "test"
                   :text text
                   :vector vect
                   :meta {:a 1 :b 2}}])
    (search-docs "test" vect 1))

  (j/execute-one! @conn
                  [(str (j/prepare @conn ["insert into doc (collection, text, vector, meta) values (?, ?, ?, ?)"
                                          "test" "Goodbye" (vec->pgvec emb)

                                          (json/write-value-as-string {:a 1 :b 2})]))])

  (j/prepare)

  )
