(ns Belvedere.pinecone
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [Belvedere.load :as load]
            [martian.hato :as http]
            [martian.core :as martian]))


(require-python '[langchain.embeddings.openai :as openai])
(require-python '[langchain.vectorstores :as vs])
(require-python '[pinecone :as pinecone])

(defonce api-key (System/getenv "PINECONE_API_KEY"))
(defonce environment (System/getenv "PINECONE_ENVIRONMENT"))

;; pinecone.init(api_key="***", environment="...")
;; index = pinecone.Index("langchain-demo")
;; embeddings = OpenAIEmbeddings()
;; vectorstore = Pinecone(index, embeddings.embed_query, "text")

(comment
  (pinecone/init :api_key api-key :environment environment)

  (def index
    (py. vs/Pinecone from_documents
         load/docs
         (openai/OpenAIEmbeddings)
         :index_name "databricks"))

  (pinecone/describe_index "databricks")


  (def res
    (py. index similarity_search "What is the smallest supported CIDR block for a databricks subnet?"))

  (py. index query "What is the smallest supported CIDR block for a databricks subnet?")

  (py.- (first res) page_content))
