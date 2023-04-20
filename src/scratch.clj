(ns user
  (:require
   [Belvedere.openai :as openai]
   [Belvedere.md :refer [md->edn]]
   [portal.api :as portal]
   [selmer.parser :refer [<<] :as selmer]
   [clojure.java.io :refer [writer] :as io]
   [clojure.core.async :refer [chan go-loop <! >! close! >!! put!]]
   [clojure.core.match :refer [match]]
   ;; require malli
   [malli.core :as m]

   ;; require python
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python :refer [py. py.. py.-] :as py]
   [clojure.string :as str]))




#_(do
  (def p (portal/open))
  (add-tap #'portal/submit))

(defn text-tap> [stuff]
  (tap> (with-meta [:portal.viewer/markdown stuff]
          {:portal.viewer/default :portal.viewer/hiccup})))

;; (require-python '[unstructured.partition.md :refer [partition_md]])
;; (require-python '[unstructured.cleaners.core :as clean])


;; Chat


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


(defn role-heading [role]
  (let [role (str/capitalize (name role))]
    (<< "\n\n# {{role}}\n\n")))



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



(comment
  (converse "/Users/selah/Drive/Belvedere/Belvedere/Conversations/Untitled.md"
            :model "gpt-4"
            :system-prompt (slurp "language/agents/cyan-bootstrap.md")
            )


  (converse "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Belvedere/Belvedere/Conversations/Untitled.md"
            :model "gpt-4"
            :system-prompt (slurp "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Belvedere/Belvedere/Agents/cyan-bootstrap.md")))
