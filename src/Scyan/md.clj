(ns Scyan.md
  (:require [Scyan.docs :as docs]
            [Scyan.super-fn :as sfn]
            [clojure.string :as str]
            [clojure.core.async :as a :refer [go-loop <! <!! >! close!]]
            [selmer.parser :refer [<<] :as selmer]
            [clojure.core.match :refer [match]]
            [Scyan.maestro :as mo]
            [malli.core :as m]
            [Scyan.openai :as openai])
  (:import [java.time Instant]
           [java.time.format DateTimeFormatter]))


(defn role-heading
  "Returns a role heading string based on the given role."
  [role]
  (str "# " role))


(defn message->md
  "Converts an OpenAI message into a Markdown string."
  [[role content]]
  (let [role-heading (role-heading role)]
    (<< "\n\n{{role-heading}}\n\n{{content|safe}}\n\n")))


(defn convo->md
  [convo]
  (->> convo
       (map message->md)
       (str/join)))

(comment (println (convo->md [[:system "system stuff"] [:user "more stuff"] [:assistant "response stuff"]])))

;; Function to convert Markdown into a collection of OpenAI messages
(defn md->convo
  ;; TODO: Need to use an actual MD parser
  "Converts a Markdown string into a conversation."
  [md]
  (->> (str/split md #"(?m)^# :")
       rest
       (map #(str/split % #"\n" 2))
       (mapv (fn [[heading content]]
               [(keyword (.toLowerCase heading))
                (str/trim content)]))))


(defn append!
  "Appends a message to a Markdown file."
  [file msg]
  (spit file (message->md msg) :append true))

;; Function to stream content from a channel to a file
(defn append-stream!
  "Streams content from a channel to a file, with an optional append? flag."
  ([c file] (append-stream! c file false))
  ([c file append?]
   (when-not append?
     (spit file ""))
   (loop []
     (when-let [v (<!! c)]
       (spit file
             (match v
                    [:content content] (spit file content :append true)
                    [:role role] (spit file (str "\n\n" (role-heading role) "\n") :append true)
                    :else "")
             :append true)
       (recur)))))


(defn front-matter [model]
  (<< "---
model: {{model}}
timestamp: tdy
---

"))



(defrecord MarkdownLog [file]
  Scyan.maestro.MaestroLog
  (init! [_ model] (spit file (front-matter model)))
  (read-all [_] (md->convo (slurp file)))
  (last-message [this] (last (mo/read-all this)))
  (append! [_ msg] (spit file (message->md msg) :append true))
  (append-stream! [_ c] (append-stream! c file true)))



(comment

  (def vault-path "/Users/selah/Drive/Scyan/")
  (def f (str vault-path "Conversations/Maestro Test.md"))
  (def log (->MarkdownLog f))
  (mo/append! log [:system "You know it is."])
  (mo/append! log [:user "I dont?!?"]))


