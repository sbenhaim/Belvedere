(ns Scyan.docs
  (:require [Scyan.load :as load]
            [clojure.java.shell :as sh]
            [hato.client :as http]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [malli.core :as m]
            [selmer.parser :as selmer :refer [<<]]
            [selmer.util :as su]
            [Scyan.openai :as openai]
            [clojure.string :as str]
            [Scyan.docs :as docs]
            [Scyan.super-fn :as sfn]))


;; Turn off selmer safe mode
(su/turn-off-escaping!)


(require-python '[bs4 :as bs]
                '[html2text :as h2t]
                '[langchain.document_loaders :as dl]
                '[langchain.text_splitter :as ts])


(defn html->md
  [html]
  (let [soup (bs/BeautifulSoup html "html.parser")
        markdown-converter (h2t/HTML2Text)]
    (py/set-attr! markdown-converter :ignore_links false)
    (py/set-attr! markdown-converter :ignore_images true)
    (py. markdown-converter handle (str soup))))


(defn lc-docs->scyan
  "Converts a list of LangChain documents to Scyan format"
  [docs]
  (doall
   (pmap (fn [doc]
           (let [text (py.- doc page_content)
                 meta (py.- doc metadata)]
             (println "Processing" meta)
             {:text text
              :meta meta
              :embedding (openai/embedding text)}))
         docs)))


(defn get-url [url]
  (:body (http/get url)))


(defn get-splitter [& {:keys [chunk-size chunk-overlap splitter-class]
                       :or {chunk-size 1000
                            chunk-overlap 50
                            splitter-class ts/MarkdownTextSplitter}}]
  (splitter-class :chunk_size chunk-size
                  :chunk_overlap chunk-overlap))


(defn split [s splitter]
  (py. splitter split_text s))


(defn load-dir
  "Loads files from a specified directory using a DirectoryLoader.

  Parameters:
  - dir: A string representing the path to the directory from which files are to be loaded.

  Optional Keyword Arguments:
  - :glob (default \"**/*\"): A string representing a glob pattern used to match files within the directory.
    The default value \"**/*\" matches all files and directories recursively.
  - :loader-class (default dl/UnstructuredFileLoader): A class representing the type of loader to be used for loading files.
    The default value is dl/UnstructuredFileLoader, which is a loader class for unstructured files.

  Returns:
  - loaded documents."
  [dir & {:keys [glob loader-class chunk-size chunk-overlap splitter]
          :or {glob "**/*"
               loader-class dl/TextLoader
               splitter (get-splitter)}}]
  (let [loader (dl/DirectoryLoader dir :glob glob :loader_cls loader-class)]
    (py. loader load_and_split (splitter chunk-size chunk-overlap))))


(defn load-site [url dir]
  (sh/sh "wget" "--recursive" "--no-clobber"
         "--page-requisites" "--html-extension"
         "--convert-links" "--restrict-file-names=windows"
         "--domains" "--no-parent" url
         :dir dir))


(def doc-schema
  [:map
   [:contents :string]
   [:embedding [:vector number?]]
   [:meta {:optional true} [:map-of :keyword :string]]])


(defn concise-embedding
  "Uses GPT 3 to concise-ify text and then creates and embedding based on the concise-ified text"
  [text]
  (openai/embedding (sfn/concise-ify text)))


(defn txt->doc [text & {:keys [meta] :or {meta {}}}]
  {:contents text
   :embedding (concise-embedding text)
   :meta meta})


(defn apply-embeddings!
  [ss]
  (pmap txt->doc ss))


(defn format-results
  [res]
  (->> res
       (map #(selmer/render "{{contents}}\n" %))
       (str/join "\n")))


(defprotocol Index
  (is-empty? [this] "Check if a collection exists in the index")
  (top-n [this q n] "Query the index for the top n documents matching the query q")
  (insert! [this doc] "Insert a document into the index")
  (insert-many! [this docs] "Insert multiple documents into the index")
  (save! [this] "Save the index to disk"))



(defn index-url
  [url index]
  (let [docs
        (-> url
            get-url
            html->md
            (split (get-splitter))
            apply-embeddings!)]
    (insert-many! index docs)))


(defn query-index
  [index q & {:keys [n model] :or {n 3}}]
  (let [res (top-n index (openai/embedding q) n)]
    #_(format-results res)
    (sfn/summarize-topic (format-results res) q
                         model {:model model})))

(defn summarize-query-index
  [index q & {:keys [n model] :or {n 5}}]
  (let [res (top-n index (openai/embedding q) n)
        summary (->> res
                     (pmap (fn [t] (sfn/context-filter q (:contents t))))
                     (remove nil?)
                     (str/join "\n"))]
    (println summary)
    (sfn/query-text q summary :model openai/chat-model)))

(comment

  ;; Pull text
  (def text (get-url "https://www.databricks.com/blog/2023/03/09/distributed-data-governance-and-isolated-environments-unity-catalog.html"))

  ;; Convert to md
  (def md (html->md text))
  (spit "test.md" md)

  ;; Split
  (def splits (split md (get-splitter)))
  (count splits)
  (first splits)
  (spit "test.md" (str/join "\n\n---\n\n" splits))

  ;; Index for search
  (require '[Scyan.scy-mem :refer [->MemoryIndex]])
  (def idx (->MemoryIndex "db-isolation"))
  ;; todo: 2-step embedding
  (def docs (apply-embeddings! splits))
  (time
    (insert-many! idx docs))

  ;; See related docs
  (->>
    (top-n idx (openai/embedding "How can I set up separate workspaces for prod and dev using Unity catalog?") 3)
    (format-results))

  (time
    (query-index idx "How can I set up separate workspaces for prod and dev using Unity catalog?"))

  (time
    (summarize-query-index idx "How can I set up separate workspaces for prod and dev using Unity catalog?" :n 10))

  (require '[Scyan.pg :refer [->PostgresIndex]])


  (let [url "https://guide.clojure.style/"
        idx (->PostgresIndexer "clojure-style-guide")]
    (-> url get-url html->md))


  (def idx (->PostgresIndex "https://github.com/bbatsov/clojure-style-guide"))

  (def res (top-n idx (openai/embedding "alignment of function arguments") 10))

  (->> res
       (pmap (fn [x] (sfn/context-filter "alignment of function arguments" (:contents x))))
       (remove nil?)
       vec)


  (vec)
  (remove nil?
          (pmap (fn [x] (sfn/context-filter "alignment of function arguments" (:contents x))) res))

  (count res2)

  (sfn/command "Clojure function for map and drop nils"))

