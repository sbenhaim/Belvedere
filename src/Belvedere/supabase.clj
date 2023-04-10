(ns Belvedere.supabase
  (:require [hato.client :as client]
            [Belvedere.openai :as openai]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.clj-kondo :as mc]))

(defonce url "https://uxttopbimogctibsfseb.supabase.co/rest/v1/")

;; Get environment variable from .env file
(defonce api-key (System/getenv "SUPABASE_API_KEY"))


(def headers {"apikey" api-key
              "Authorization" (str "Bearer " api-key)})

(defn call-supa
  "Performs an HTTP request to the Supabase API."
  [method path data]
  (let [full-url (str url "/" path)]
    (client/request {:method method
                     :url full-url
                     :headers headers
                     :form-params data
                     :content-type :json
                     :as :json})))

(defn swrite
  "Writes data to a specified table in Supabase."
  [table data]
  (call-supa :post table data))


(defn sread
  "Reads data from a specified path in Supabase."
  [path]
  (call-supa :get path {:select "*"}))


(defn vec->pgvec
  "Converts a Clojure vector to a PostgreSQL array representation."
  [vec]
  (str "[" (str/join "," vec) "]"))


(defn search
  "Searches for documents in Supabase."
  [col query k]
  (let [query (openai/embedding query)
        data {:col col
              :embedding (vec->pgvec query)
              :k k}
        res (call-supa :post "rpc/search_docs" data)]
    (get-in res [:body])))


(defn search-prompts
  "Searches for prompts in Supabase."
  [query k]
  (search "prompts" query k))


(def doc-schema [:map
                 [:text :string]
                 [:embedding [:vector float?]]
                 [:meta [:map-of :keyword :any]]])


(defn write-docs
  "Writes documents to a specified collection in Supabase."
  [col docs & {:keys [partition-size] :or {partition-size 100}}]
  (let [chunks (partition-all partition-size docs)]
    (doseq [doc-chunk chunks]
      (println "Writing" (count doc-chunk) "documents to" col)
      (let [data (for [doc doc-chunk]
                   {:collection col
                    :text (:text doc)
                    :vector (vec->pgvec (:embedding doc))
                    :meta (:meta doc)})]
        (swrite "docvec" data)))))


(m/=> write-docs [:=> [:cat :string [:sequential doc-schema]] :any])
