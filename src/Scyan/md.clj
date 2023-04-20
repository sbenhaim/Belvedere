(ns Scyan.md
  (:require [clojure.string :as str]
            [clojure.core.async :refer [go-loop <! >! close!]]
            [selmer.parser :refer [<<] :as selmer]
            [clojure.core.match :refer [match]]
            [Belvedere.maestro :as mo]
            [malli.core :as m]
            [Belvedere.openai :as openai]))

;; Function to convert Markdown into a collection of OpenAI messages
(defn md->openai-messages
  "Converts a Markdown string into a collection of OpenAI messages."
  [md]
  (->> (str/split md #"(?m)^# ")
       rest
       (map #(str/split-lines %))
       (mapv (fn [r]
               [(keyword (.toLowerCase (first r)))
                (str/join "" (rest r))]))))

;; Function to generate role heading string
(defn role-heading
  "Returns a role heading string based on the given role."
  [role]
  (let [role-str (str/capitalize (name role))]
    (<< "\n\n# {{role-str}}\n\n")))

;; Function to stream content from a channel to a file
(defn stream-to-file
  "Streams content from a channel to a file, with an optional append? flag."
  ([c file] (stream-to-file c file false))
  ([c file append?]
   (when-not append?
     (spit file ""))
   (go-loop []
     (when-let [v (<! c)]
       (spit file
             (match v
                    [:content content] (spit file content :append true)
                    [:role role] (spit file (role-heading role) :append true)
                    :else "")
             :append true)
       (recur)))))

;; MarkdownLog type definition
(deftype MarkdownLog [file]
  mo/MaestroLog
  (get-log [_] (md->openai-messages (slurp file)))
  (last-message [this] (last (mo/get-log this)))
  (append! [_ [role content]] (spit file (str (role-heading role) content) :append true))
  (streaming-append! [_ c] (stream-to-file c file true)))
