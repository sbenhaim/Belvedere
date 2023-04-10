(ns Belvedere.supabase
  (:require [hato.client :as client]
            [Belvedere.openai :as openai]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.clj-kondo :as mc]))


;; Define the base URL for the Supabase API.
(defonce url "https://uxttopbimogctibsfseb.supabase.co/rest/v1/")

;; Define the API key from the environment variable.
(defonce api-key (System/getenv "SUPABASE_API_KEY"))

;; Define the headers for the Supabase API requests.
(def headers {"apikey" api-key
              "Authorization" (str "Bearer " api-key)})


(defn call-supa
  "Performs an HTTP request to the Supabase API.
  - method: The HTTP method (e.g., :get, :post).
  - path: The API endpoint path.
  - data: The request data (e.g., form parameters)."
  [method path data]
  (let [full-url (str url "/" path)]
    (client/request {:method method
                     :url full-url
                     :headers headers
                     :form-params data
                     :content-type :json
                     :as :json})))


(defn swrite
  "Writes data to a specified table in Supabase.
  - table: The name of the table to write to.
  - data: The data to write to the table."
  [table data]
  (call-supa :post table data))


(defn sread
  "Reads data from a specified path in Supabase.
  - path: The API endpoint path to read from."
  [path]
  (call-supa :get path {:select "*"}))


(defn vec->pgvec
  "Converts a Clojure vector to a PostgreSQL array representation.
  - vec: The Clojure vector to convert."
  [vec]
  (str "[" (str/join "," vec) "]"))


(defn search
  "Searches for documents in Supabase.
  - col: The column to search in.
  - query: The search query.
  - k: The number of results to return."
  [col query k]
  (let [query (openai/embedding query)
        data {:col col
              :embedding (vec->pgvec query)
              :k k}
        res (call-supa :post "rpc/search_docs" data)]
    (get-in res [:body])))


(defn search-prompts
  "Searches for prompts in Supabase.
  - query: The search query.
  - k: The number of results to return."
  [query k]
  (search "prompts" query k))


;; Define the schema for a document.
(def doc-schema [:map
                 [:text :string]
                 [:embedding [:vector float?]]
                 [:meta [:map-of :keyword :any]]])


(defn write-docs
  "Writes documents to a specified collection in Supabase.
  - col: The name of the collection to write to.
  - docs: The documents to write.
  - partition-size: (optional) The number of documents to write in each batch."
  [col docs & {:keys [partition-size] :or {partition-size 100}}]
  (let [chunks (partition-all partition-size docs)]
    (doseq [doc-chunk chunks]
      (println "Writing" (count doc-chunk)
               "documents to" col)
      (let [data (for [doc doc-chunk]
                   {:collection col
                    :text (:text doc)
                    :vector (vec->pgvec (:embedding doc))
                    :meta (:meta doc)})]
        (swrite "docvec" data)))))


;; Define the schema for the write-docs function using malli.
(m/=> write-docs [:=> [:cat :string [:sequential doc-schema]] :any])
