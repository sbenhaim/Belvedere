(ns Scyan.md
  (:require [clojure.string :as str]
            [clojure.core.async :refer [go-loop <! >! close!]]
            [selmer.parser :refer [<<] :as selmer]
            [clojure.core.match :refer [match]]
            [Scyan.maestro :as mo]
            [malli.core :as m]
            [Scyan.openai :as openai]))


(defn md->openai-messages [md]
  (->> (str/split md #"(?m)^# ")
       rest
       (map #(str/split-lines %))
       (mapv (fn [r]
               [(keyword (.toLowerCase (first r)))
                (str/join "" (rest r))]))))


(defn role-heading
  [role]
  (let [role-str (str/capitalize (name role))]
    (<< "\n\n# {{role-str}}\n\n")))



(defn stream-to-file
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


(deftype MarkdownLog [file]
  mo/MaestroLog
  (get-log [_] (md->openai-messages (slurp file)))
  (last-message [this] (last (mo/get-log this)))
  (append! [_ [role content]] (spit file (str (role-heading role) content) :append true))
  (streaming-append! [_ c] (stream-to-file c file true)))
