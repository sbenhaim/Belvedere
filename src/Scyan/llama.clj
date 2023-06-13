(ns Scyan.llama
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]
            [portal.api :as portal]))



(require-python '[llama_index :as llama])
(require-python '[langchain.chat_models :as chat_models])

(def gpt3-predictor
  (llama/LLMPredictor :llm (chat_models/ChatOpenAI :temperature 0 :model_name "gpt-3.5-turbo")))

(def gpt4-predictor
  (llama/LLMPredictor :llm (chat_models/ChatOpenAI :temperature 0 :model_name "gpt-4")))

(def max-input-size 4096)
(def num-output 512)
(def max-chunk-overlap 20)
(def prompt-helper (llama/PromptHelper max-input-size num-output max-chunk-overlap))

(def gpt4-sc (py. llama/ServiceContext from_defaults :llm_predictor gpt4-predictor :prompt_helper prompt-helper))
(def gpt3-sc (py. llama/ServiceContext from_defaults :llm_predictor gpt3-predictor :prompt_helper prompt-helper))

; index = GPTSimpleVectorIndex.load_from_disk('scyan_py/indices/databricks.json')


(defn index-nodes
  [nodes]
  (llama/GPTSimpleVectorIndex nodes :service_context gpt4-sc))

(comment

  (def db-index
    (py. llama/GPTSimpleVectorIndex load_from_disk "../indices/databricks.json"))

  (def nv-index
    (py. llama/GPTSimpleVectorIndex load_from_disk "../indices/neovim.json"))


  ;; response = index.query("""Describe the various cluster access modes and how they can be used with Unity Catalog and Hive metastore. Format results in Markdown.

  ;; """, service_context=gpt4_sc)


  (time
   (let [q "What does the tiebreak function do?"
         response (py. nv-index query :query_str q :service_context gpt4-sc)]
     (text-tap
      (py.- response response)))))
