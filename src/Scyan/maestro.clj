(ns Scyan.maestro
  (:require
   [Scyan.docs :as docs]
   [Scyan.openai :as openai]
   [Scyan.pg :as pg]
   [Scyan.search :as search]
   [Scyan.wa :as wa]
   [Scyan.super-fn :refer [command] :as sfn]
   [libpython-clj2.python :as py]
   [clojure.core.async :refer [<!! <! go-loop] :as a]
   [clojure.core.match :refer [match]]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [malli.core :as m]
   [jsonista.core :as json]
   [selmer.parser :refer [<<]]))


(defn code-block
  ([contents] (code-block "" contents))
  ([lang contents]
   (str "```" lang "\n" contents "\n```")))


(defn safe-read-string [s]
  (try
    (read-string s)
    (catch RuntimeException _
      nil)))


(defn safe-read-json [j]
  (try
    (json/read-value j json/keyword-keys-object-mapper)
    (catch com.fasterxml.jackson.core.JsonParseException _
      nil)))



(defn parse-dispatch
  [response]
  (let [[_ lang code] (re-find #"(?s)```([\w-]+)(.*?)```" response)]
    (when (and lang code)
      (let [d (safe-read-json code)
            l (keyword (str/trim lang))]
        (if (instance? clojure.lang.IPersistentMap d)
          [l d]
          [l (str/trim code)])))))


;; TODO: Process for creating, storing, and accessing compressed chat history
(defprotocol MaestroLog
  (init! [this model])
  (read-all [this])
  (last-message [this])
  (append! [this message])
  (append-stream! [this c])
  (swap-roles! [this]))


(extend-type clojure.lang.Atom
  MaestroLog
  (init! [this _]
    (reset! this (atom [])))
  (read-all [this] (deref this))
  (last-message [this] (-> this deref last))
  (append! [this message] (swap! this conj message))
  (append-stream! [this c] (let [n (count @this)]
                             (a/go-loop []
                               (when-let [v (<!! c)]
                                 (match v
                                        [:role role] (swap! this conj [(keyword role) ""])
                                        [:content content] (swap! this update-in [n 1] str content)
                                        :else nil)
                                 (recur))))))


(comment
  (def a (init! (atom nil) "gpt-4"))
  (append! a [:system "You are a helpful assistant"])
  (append! a [:user "Wazzup with you?"])
  (last-message a)
  (read-all a)
  (def c (a/chan))
  (append-stream! a c)
  a
  (a/close! c)
  )



(def maestro-log-schema
  (m/-simple-schema {:type ::maestro-log
                     :pred (partial satisfies? MaestroLog)}))


(defmulti dispatch first)

(defmethod dispatch :default [resp]
  nil)


(defn user-confirms [prompt]
  (println prompt)
  (= (read-line) "y"))


(defn assistant-requested-action
  [response]
  (let [res (parse-dispatch response)]
    (when (m/validate [:tuple :keyword :any] res)
      res)))


(defn maestro-log-stream
  [log model & {:keys [step-limit dry-run swap-roles]
                :or {step-limit 10
                     dry-run false
                     swap-roles false}}]
  (loop [log log step 0]
    (when (< step step-limit)
      (let [log-contents (read-all log)
            log-contents (if swap-roles (openai/swap-roles log-contents) log-contents)
            [role _] (last log-contents)]
        (case role
          :assistant (do (println "Dispatching assistant response")
                         (let [[_ response] (last-message log)
                               action (assistant-requested-action response)]
                           (when action
                             (if dry-run
                               (do (println "Dry run")
                                   action)
                               (when-let [ext-res (dispatch action)]
                                 (println "Wet run")
                                 (append! log [:user ext-res])
                                 (recur log (inc step)))))))
          (do (println "Dispatching user response")
              (let [_ (clojure.pprint/pprint (read-all log))
                    rc (openai/chat (read-all log) :stream true :model model)
                    file-stream (a/chan)]
                (a/tap (a/mult rc) file-stream)
                (append-stream! log file-stream))
              (recur log (inc step))))))))


(defn new-workflow
  [request & {:keys [log model scy-prompt]
              :or {log (atom [])
                   model openai/chat-model
                   scy-prompt openai/default-system-prompt}}]
  (init! log model)
  (append! log (openai/new-convo request scy-prompt))
  (maestro-log-stream log model))



(defmethod dispatch :wolfram [[_ query]]
  (let [res (wa/query query)]
    (code-block "" res)))



(defmethod dispatch :python [[_ code]]
  (let [res (sh "ipython" "--classic" "-c" code :dir "/Users/selah/Drive/Scyan/")
        result (str/trim (:out res))]
    (if (or (str/blank? result)
            (re-find #"Shell is already running a gui event loop for osx" result))
      "Done."
      (code-block "" result))))

(comment (dispatch [:python (slurp "/Users/selah/Drive/Scyan/tmp.py")]))

(defmethod dispatch :bash [[_ code]]
  (let [res (sh "sh" "-c" code)
        result (str/trim (res :out))]
    (if (or (str/blank? result)
            (re-find #"Shell is already running a gui event loop for osx" result))
      "Done!"
      (code-block "" result))))



(defmethod dispatch :clojure [[_ code]]
  (code-block "clojure"
              (eval (read-string code))))


#_(defmethod dispatch :web-search [[_ q]]
    (search/search-for q 3))


(defmethod dispatch :web-search [[_ q]]
  (let [res (search/query q 1)
        {:keys [link title snippet]} res
        link (str/replace (str/trim link) "\"" "")]
    (dispatch [:query-url {:url link :query q}])))


(defmethod dispatch :query-url [[_ {:keys [url query]} :as args]]
  (println "Querying URL")
  (pr-str args)
  (let [idx (pg/->PostgresIndex url)]
    (when (docs/is-empty? idx)
      (docs/index-url url idx))
    (docs/query-index idx query)))


(defmethod dispatch :remember [[_ text]]
  (let [idx (pg/->PostgresIndex "scyan-memory")]
    (docs/insert! idx (docs/txt->doc text))))


(defmethod dispatch :recall [[_ text]]
  (let [idx (pg/->PostgresIndex "scyan-memory")]
    (docs/query-index idx text)))


(defmethod dispatch :llm [[_ {:keys [model system user]}]]
  (command user :model model :sys-prompt system))


(def agent-index :todo)


(defn assign-agent
  [req]
  (let [candidates (docs/top-n agent-index req 5)
        agent (sfn/select candidates
                          (str "Which of these agents can post capably handle the following request: "
                               req))]
    (openai/new-convo req agent)))




(comment

  (dispatch [:wolfram "One dollar bill issuance"])

  (def idx (pg/->PostgresIndex "https://www.accuweather.com/en/us/minneapolis/55415/daily-weather-forecast/348794"))
  (docs/index-url "https://www.accuweather.com/en/us/minneapolis/55415/daily-weather-forecast/348794" idx)
  (docs/query-index idx "What is the 12 day forecast for minneapolis?")
  (dispatch [:wolfram "latest updates Madrid Open tennis"])

(dispatch [:query-url "langchain" "Getting started guide content"])

  (docs/format-results
   (docs/query (pg/->PostgresIndex "langchain") (openai/embedding "default vector databsse") 3))

  (require '[Scyan.md :refer [->MarkdownLog]])
  (do
    (def vault-path "/Users/selah/Drive/Scyan/")
    (def f (str vault-path "Conversations/github query.md"))
    (def scy-prompt (slurp (str vault-path "Agents/dispatch2.md")))
    (def log (->MarkdownLog f))
    (new-workflow "Download the codebase for `https://github.com/yoheinakajima/babyagi` into my `~/code` directory" :log log :model "gpt-4" :scy-prompt scy-prompt))


  (maestro-log-stream log "gpt-4")

    (dispatch [:python "from datetime import datetime; datetime.now().strftime(\"%Y-%m-%d %H:%M:%S\")"])

  (dispatch
    (parse-dispatch"\n```python\nimport pandas as pd\n\n# Change row and column print limits\npd.set_option(\"display.max_rows\", None)\npd.set_option(\"display.max_columns\", None)\n\n# Read the CSV file\nfile_path = \"/Users/selah/Desktop/Analytics Visualization Data.csv\"\ndata = pd.read_csv(file_path)\n\n# Display the first few rows of the data\nprint(data.head())\n\n# Produce summary statistics\nsummary = data.describe()\nprint(summary)\n```"))

  )
