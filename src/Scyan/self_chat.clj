(ns Scyan.self-chat
  (:require [Scyan.maestro :as maestro]
            [Scyan.openai :as openai]))


(def default-step-limit 1)


(defn converse!
  [log & {:keys [user-sys-prompt asst-sys-prompt user-model asst-model
                 step-limit]}]
  (let [[last-role _] (maestro/last-message log)]
    (case last-role
      :user (maestro/maestro-log-stream log
                                        user-model
                                        :step-limit step-limit
                                        :swap-roles false
                                        :dry-run false)
      :assistant (maestro/maestro-log-stream log
                                             asst-model
                                             :step-limit step-limit
                                             :swap-roles true
                                             :dry-run false))))
