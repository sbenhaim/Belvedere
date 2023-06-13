(ns user
  (:require
   [Scyan.docs :as docs]
   [Scyan.md :refer [md->convo]]
   [Scyan.scy-mem :as mem]
   [Scyan.super-fn :as sfn]
   [Scyan.openai :as openai]
   [clojure.core.async :refer [chan go-loop <! >! close! >!! put!]]
   [clojure.core.match :refer [match]]
   [clojure.java.io :refer [writer] :as io]
   [clojure.string :as str]
   [libpython-clj2.python :refer [py. py.. py.-] :as py]
   [libpython-clj2.require :refer [require-python]]
   [malli.clj-kondo :as mc]
   [malli.clj-kondo :as mc]
   [malli.core :as m]
   [malli.dev :as dev]
   [malli.dev.pretty :as pretty]
   [malli.instrument :as mi]
   [jsonista.core :as json]
   [portal.api :as portal]
   [selmer.parser :refer [<<] :as selmer]
   [Scyan.pg :as pg]))



(do
  (def p (portal/open))
  (add-tap #'portal/submit))

(defn text-tap> [stuff]
  (tap> (with-meta [:portal.viewer/markdown stuff]
          {:portal.viewer/default :portal.viewer/hiccup})))

;; (require-python '[unstructured.partition.md :refer [partition_md]])
;; (require-python '[unstructured.cleaners.core :as clean])


;; Chat

"You still there?"

(openai/chat (openai/new-convo "Provide vega-lite schema for a pie chart representing the weight of animal groups on earth."))
(tap> (openai/new-convo "Provide vega-lite schema for a pie chart representing the weight of animal groups on earth."))
(def c @p)
(def c (json/read-value c))
(def c (assoc c "$schema" "https://vega.github.io/schema/vega-lite/v4.json"))
(tap> c)



(defn stream-to-stout
  [c]
  (println "Starting stream")
  (go-loop []
    (match (<! c)
           {:choices [{:delta {:content content}}]} (do (print content)
                                                        (flush)
                                                        (recur))
           {:choices [{:delta {:role role}}]} (do (print (str "## " role "\n"))
                                                  (flush)
                                                  (recur))
           :else (close! c))))





(defn stream-to-file
  ([c file] (stream-to-file c file false))
  ([c file append?]
   (when-not append?
     (spit file ""))
   (go-loop []
     (match (<! c)
            {:choices [{:delta {:content content}}]} (do (spit file content :append true)
                                                         (recur))
            {:choices [{:delta {:role role}}]} (do (spit file (role-heading role) :append true)
                                                   (recur))
            :else (close! c)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def messages-schema
  [:vector
   [:map
    {:closed true}
    [:role [:enum :system :user :assistant]]
    [:content :string]]])


(defn converse
  [convo-md & {:keys [model system-prompt]
               :or {model openai/chat-model
                    system-prompt openai/default-system-prompt}}]
  (let [system (openai/new-convo system-prompt)
        convo (vec (concat system (md->edn (slurp convo-md))))]
    (stream-to-file
     (openai/chat convo
                  :model model
                  :stream true)
     convo-md
     true)
    convo))


(m/validate [:tuple :keyword [:map-of :keyword :any]] [:a {:b 1}])


(dev/start! {:report (pretty/reporter)})

(def message [:tuple [:enum :system :user :assistant] :string])


(defn last-message
  "Docs"
  {:malli/schema [:=> [:cat [:vector message]] message]}
  [messages]
  (last messages))

(mi/collect!)
(mc/emit!)
;; (mi/instrument!)


(dev/stop!)
(last-message [[:usr "things"]])



(comment
  (converse "/Users/selah/Drive/Belvedere/Belvedere/Conversations/Untitled.md"
            :model "gpt-4"
            :system-prompt (slurp "language/agents/cyan-bootstrap.md")
            )


  (converse "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Belvedere/Belvedere/Conversations/Untitled.md"
            :model "gpt-4"
            :system-prompt (slurp "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Belvedere/Belvedere/Agents/cyan-bootstrap.md"))


  (def dcs
    (let [url "https://python.langchain.com/en/latest/modules/indexes/getting_started.html"]
      (-> url
          docs/get-url
          docs/html->md
          (docs/split (docs/get-splitter))
          docs/apply-embeddings!)))

  (pg/insert-docs! dcs "langchain")

  (pg/search-docs "langchain" (openai/embedding "question") 3)

  )

(def hiccup [:h1 "hello, world"])

(tap> [:h1 "Some Headling"])

(tap> (with-meta hiccup {:portal.viewer/default :portal.viewer/hiccup}))

(dotimes [i 10]
  (tap> i))

(def a 2)

(tap> a)

(tap> "
{
  \"$schema\": \"https://vega.github.io/schema/vega-lite/v5.json\",
  \"description\": \"A simple bar chart with embedded data.\",
  \"data\": {
    \"values\": [
      {\"a\": \"A\", \"b\": 28},
      {\"a\": \"B\", \"b\": 55},
      {\"a\": \"C\", \"b\": 43},
      {\"a\": \"D\", \"b\": 91},
      {\"a\": \"E\", \"b\": 81},
      {\"a\": \"F\", \"b\": 53},
      {\"a\": \"G\", \"b\": 19},
      {\"a\": \"H\", \"b\": 87},
      {\"a\": \"I\", \"b\": 52}
    ]
  },
  \"mark\": \"bar\",
  \"encoding\": {
    \"x\": {\"field\": \"a\", \"type\": \"ordinal\"},
    \"y\": {\"field\": \"b\", \"type\": \"quantitative\"}
  }
}
")

(require-python '[scyan_py.url_to_markdown :as umd])

(umd/url_to_markdown "https://cljdoc.org/d/djblue/portal/0.40.0/doc/remote-api")

(docs/index-url
 "https://book.babashka.org/" (pg/->PostgresIndex "https://book.babashka.org/"))


(def idx (pg/->PostgresIndex "https://book.babashka.org/"))



(let [res (docs/top-n idx (openai/embedding "What are pods") 3)]
  (doseq [doc (reverse res)]
    (text-tap> (:contents doc))))


(text-tap> (sfn/command (str (nth dx 5) "\n\nA one-sentance summary of the text above could be")))

(tap> "Yello")
