(ns Belvedere.docsearch
  (:require [Belvedere.load :as load]
            [Belvedere.supabase :as supabase]
            [Belvedere.prompts :as prompts]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [libpython-clj2.require :refer [require-python]]
            [malli.core :as m]
            [Belvedere.openai :refer [embedding] :as openai]
            [hato.client :as hato]))


(require-python '[langchain.document_loaders :as dl])


(defn lc-docs->belvedere
  "Converts a list of LangChain documents to Belvedere format"
  [docs]
  (doall
   (pmap (fn [doc]
           (let [text (py.- doc page_content)
                 meta (py.- doc metadata)]
             (println "Processing" meta)
             {:text text
              :meta meta
              :embedding (embedding text)}))
         docs)))

(m/=> lc-docs->belveder [:=> [:cat [:sequential :any]] [:sequential supabase/doc-schema]])


(defn upload-dir
  "Uploads the docs to the database"
    [name dir & opts]
    (let [raw-docs (load/load-directory dir opts)
          docs (lc-docs->belvedere raw-docs)]
      (supabase/write-docs name docs)))


(defn process-file
  "Parses, splits, vectorizes, and uploads a single file"
  [name file]
  (let [chunks (load/load-md file)
        docs (lc-docs->belvedere chunks)]
    (supabase/write-docs name docs)))


(defn upload-url
  "Uploads the docs to the database"
  []
  :TODO)



(defn query-docs [collection query]
  (let [references (supabase/search collection query 2)
        refs (for [r references] {:title (get-in r [:meta :title])
                                  :text (r :text)})
        prompt (prompts/render-prompt "qa-doc" {:query query
                                                :references refs})]
    (openai/query prompt)))


(comment

  (query-docs "databricks" "What is the syntax for creating a SQL table?")

  )
