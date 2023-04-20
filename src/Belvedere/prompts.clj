(ns Belvedere.prompts
  (:require [selmer.parser :as template]
            [Belvedere.supabase :as supabase]
            [Belvedere.openai :as openai]))

(defn save [prompt]
  (let [meta-data {:inputs (template/known-variables prompt)}]
    (supabase/write-docs "prompts" [{:text prompt
                                     :vector (openai/embedding prompt)
                                     :meta meta-data}])))

(defn render-prompt [prompt inputs]
  (let [file (str "prompts/" prompt ".txt")]
    (template/render-file file inputs)))
