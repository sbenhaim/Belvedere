(ns Scyan.obsidian
  (:require [Scyan.md :as md]
            [Scyan.openai :as openai]
            [Scyan.maestro :as maestro]
            [selmer.parser :refer [<<]]
            [Scyan.maestro :as mo]))


(defn front-matter->str [dict]
  (<< "---
{% for k, v in dict %}{{k|name}}: {{v|safe}}
{% endfor %}---
"))


(defn init-olog! [vault-root convo-path sysprompt-path model]
  (let [front-matter {:timestamp (str (java.util.Date.))
                      :model model
                      :prompt (str "[[" sysprompt-path "]]")}
        convo-full (str vault-root "/" convo-path)]
    (spit convo-full (front-matter->str front-matter)
          :append false)))


(defrecord ObsidianLog [vault-root convo-path sysprompt-path model]
  maestro/MaestroLog
  (init! [_ _] (init-olog! vault-root convo-path sysprompt-path model))
  (read-all [_] (let [convo (md/md->convo (slurp (str vault-root "/" convo-path)))
                      prompt (slurp (str vault-root "/" sysprompt-path))]
                  (into [[:system prompt]] convo)))
  (last-message [this] (last (maestro/read-all this)))
  (append! [_ msg] (spit (str vault-root "/" convo-path) (md/message->md msg) :append true))
  (append-stream! [_ c] (md/append-stream! c (str vault-root "/" convo-path) true)))



(defn converse!
  [convo-path sysprompt-path model]
  (let [system (openai/new-convo nil (slurp sysprompt-path))
        convo (vec (concat system (md/md->convo (slurp convo-path))))]
    (md/append-stream!
     (openai/chat convo
                  :model model
                  :stream true)
     convo-path
     true)
    convo))


(defn interact!
  [vault-path convo-path sysprompt-path model & opts]
  (let [log (->ObsidianLog vault-path convo-path sysprompt-path model)]
    (apply maestro/maestro-log-stream log model opts)))


(comment
 (converse! "/Users/selah/Drive/Scyan/Scyan/Conversations/Core Async.md"
            "gpt-4"
            (slurp "/Users/selah/Drive/Scyan/Scyan/Agents/cyan-bootstrap.md"))

 (interact! "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Scyan" "Conversations/Analytics Visualization Challenge.md" "Agents/tool-dispatch.md" "gpt-4"
            :step-limit 10 :dry-run true)


 (interact! "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Scyan"
            "Conversations/ObsidianLog.md"
            "Agents/dispatch2.md"
            "gpt-4"
            :step-limit 10)


 (interact! "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Scyan"
            "Conversations/self-talk.md"
            "Agents/user-roleplay.md"
            "gpt-3.5-turbo"
            :step-limit 1
            :swap-roles false)

 (md/md->convo (slurp "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Scyan/Conversations/self-talk.md"))

 (interact! "/Users/selah/Library/CloudStorage/GoogleDrive-selahb@gmail.com/My Drive/Scyan" "Conversations/Architectural Assessment.md" "Agents/default.md" "4"
            :step-limit 10 :dry-run true))


