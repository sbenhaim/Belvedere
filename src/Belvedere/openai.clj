(ns Belvedere.openai
  (:require [wkok.openai-clojure.api :as api]))


(def embedding-model "text-embedding-ada-002")


(defn embedding
  "Returns the embedding for the given text."
  [text]
  (let [embedding (api/create-embedding
                   {:input text
                    :model embedding-model})]
    (get-in embedding [:data 0 :embedding])))


(defn query
  "Uses OpenAI chat API to query the given text."
  ([text]
   (query text "gpt-3.5-turbo"))
  ([text model]
   ; TODO: role should come from role management
   ; TODO: Validate prompt fits within max_tokens
    (let [messages [{:role "system" :content "You are a helpful assistant."}
                    {:role "user" :content text}]
          response (api/create-chat-completion
                    {:model model
                     :messages messages
                     :max_tokens 2048
                     :temperature 0.7
                     :top_p 1
                     :frequency_penalty 0.0
                     :presence_penalty 0.0
                     :stop ["\n"]})]
      (get-in response [:choices 0 :message :content]))))


(query "Why is the sky blue?")

(comment

  (embedding "Hello"))
