(ns Scyan.maestro
  (:require [Scyan.openai :as openai]
            [Scyan.wa :as wa]
            [clojure.edn :as edn]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [clojure.core.async :refer [<!!] :as a]))

(def system-prompt "When a user request involves a mathematical formula or calculation, utilize the Wolfram Alpha API and respond with the EDN data structure: [:wolfram-alpha \"{{calculation}}\"]]")


(defn process-wolfram-alpha [{:keys [content] :as response}]
  (if-let [match (re-find #"\[:wolfram-alpha \"(.+?)\"\]" content)]
    (openai/message (str "The answer is " (wa/result (second match))))
    response))



(defn parse-response [response]
  (let [code-fence "```clojure"
        len-cf (count code-fence)
        start (+ len-cf (str/index-of response code-fence))
        end (str/index-of response "```" start)
        edn-str (str/trim (subs response start end))]
    (edn/read-string edn-str)))


(defprotocol MaestroLog
  (get-log [storage])
  (last-message [storage])
  (append! [storage message])
  (streaming-append! [storage c]))


(extend-type clojure.lang.Atom
  MaestroLog
  (get-log [a] @a)
  (last-message [a] (last @a))
  (append! [a message] (swap! a conj message))
  (streaming-append! [a c] (swap! a conj (openai/collect-streaming-response c))))


(let [a (atom (openai/new-convo))]
  (append! a (openai/message "What is 3 + 4?"))
  (streaming-append! a (openai/chat @a :stream true))
  (get-log a))


(defn tool-response [success tool response & opts]
  (merge
   opts
   {:response response
    :from [:tool tool]
    :success success}))


(defmulti dispatch :tool)


(defn maestro
  [response]
  (let [instr (parse-response response)]
    (dispatch instr)))


(defn maestro-conduct
  [request & {:keys [log]
              :or {log (atom [])}}]
  (append! log (openai/convo system-prompt))
  (append! log [:user request])
  (loop [log log]
    (let [rc (openai/chat (get-log log) :stream true)
          rmult (a/mult rc)]
      (streaming-append! log (a/tap rmult (a/chan)))
      (let [response (openai/collect-streaming-response (a/tap rmult (a/chan)))]
        (append! log response)
        (when-not (response :stop)
          (let [res (parse-response response)
                ext-res (dispatch res)]
            (append! log [:user ext-res])
            (recur log)))))))



(defmethod dispatch :default [resp]
  (openai/message (str "I don't know how to " (get resp :content))))

(defmethod dispatch :file-reader [{:keys [params]}]
  (tool-response true :file-reader (slurp (params :path))))

(defmethod dispatch :default [resp])

(defmethod dispatch :subordinate [resp])

(defmethod dispatch :tool [resp])



(comment

  (let [response "
To read the contents of the file at \"~/Desktop/gpt.txt\", I will use the file-reader tool by providing the appropriate EDN structure:

```clojure
{:tool :file-reader
 :params {:path \"/Users/selah/Desktop/gpt.txt\"}}
```
"]
    (-> response
        parse-response
        dispatch))

  (do
    (def convo (atom (openai/new-convo system-prompt)))
    (chat-with-history convo "What is 1234 * 5678?"))
  @convo)
