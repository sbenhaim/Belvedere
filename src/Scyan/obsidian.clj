(ns Scyan.obsidian
  (:require [Scyan.md :as md]
            [Scyan.openai :as openai]))


(defn converse!
  [convo-md model system-prompt]
  (let [system (openai/new-convo system-prompt)
        convo (vec (concat system (md/md->openai-messages (slurp convo-md))))]
    (md/stream-to-file
     (openai/chat convo
                  :model model
                  :stream true)
     convo-md
     true)
    convo))



(comment
 (converse! "/Users/selah/Drive/Scyan/Scyan/Conversations/Core Async.md"
            "gpt-4"
            (slurp "/Users/selah/Drive/Scyan/Scyan/Agents/cyan-bootstrap.md")))
