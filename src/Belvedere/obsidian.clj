(ns Belvedere.obsidian
  (:require [Belvedere.md :as md]
            [Belvedere.openai :as openai]))


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



(converse! "/Users/selah/Drive/Belvedere/Belvedere/Conversations/Core Async.md"
           "gpt-4"
           (slurp "/Users/selah/Drive/Belvedere/Belvedere/Agents/cyan-bootstrap.md"))
