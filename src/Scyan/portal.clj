(ns Scyan.portal
  (:require [Scyan.openai :as openai]
            [clojure.core.async :as async :refer [<!]]
            [clojure.core.match :refer [match]]
            [Scyan.maestro :as maestro]))


(defn as-hiccup
  [content]
  (with-meta content
             {:portal.viewer/default :portal.viewer/hiccup}))


(defn as-markdown
  [content]
  (with-meta [:portal.viewer/markdown content]
             {:portal.viewer/default :portal.viewer/hiccup}))



(defn convo->portal
  [convo]
  (for [[role content] convo]
    [(as-hiccup [:h1 (name role)])
     (as-markdown content)]))


(defn stream-to-atom
  [c a]
  (async/go-loop []
    (when-let [v (<! c)]
      (match v
             [:role role] (swap! a update-in [1] str "\n\n# " (keyword role) "\n\n")
             [:content content] (swap! a update-in [1] str content)
             :else (println v))
      (recur))))


(defn new-output-atom []
  (atom (as-markdown "")))


(defn chat-in-portal
  [convo-atom & {:as args}]
  (let [[role content] (last @convo-atom)
        output         (new-output-atom)
        c (apply openai/chat @convo-atom :stream true args)
        m (async/mult c)]
    (tap> output)
    (swap! output update-in [1] str "\n\n# " (keyword role) "\n\n" content "\n\n")
    (stream-to-atom (async/tap m (async/chan)) output)
    (openai/append-streaming-response! (async/tap m (async/chan)) convo-atom)))




(comment

  (require '[portal.api :as portal])


  (do
    (def convo (atom (openai/new-convo "You are PyGenGPT. Your job is to produce python code in response to user requests. Your code will be run directly, so do not provide additional output other than the code.\n\nHow many vowels are there in the word \"Mississippi\"?"
                                       #_"You are PyGenGPT. Your job is to produce python code in response to user requests. Your code will be run directly, so do not provide additional output other than the code."
                                       )))
    (portal/clear)
    (chat-in-portal convo
                    :model "gpt-4"
                    :temperature 0.7))

  (tap> convo)

  (do
    (swap! convo conj [:user "What is the current date and time?"])
    (chat-in-portal convo
                    :model "gpt-4"
                    :temperature 0.7))

  (tap> (openai/convo->str @convo))
  (def cmp (openai/compress-convo @convo))
  (tap> (openai/convo->str cmp)))
