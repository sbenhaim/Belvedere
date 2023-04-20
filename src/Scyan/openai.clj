(ns Scyan.openai
  (:require [wkok.openai-clojure.api :as api]
            [malli.core :as m]
            [clojure.core.match :refer [match]]
            [clojure.java.io :refer [writer]]
            [clojure.core.async :refer [mult go go-loop <! >! chan close! timeout >!! <!! put! tap onto-chan!] :as a]))


(def embedding-model "text-embedding-ada-002")
(def chat-model "gpt-3.5-turbo")
(def default-system-prompt "You are a helpful assistant.")


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
                     :presence_penalty 0.0})]
      (get-in response [:choices 0 :message :content]))))


(defn new-convo
  "Provides a starting conversation"
  ([]
   (new-convo default-system-prompt))
  ([system-prompt]
   [[:system system-prompt]]))

; malli function schema for message, where role is one of :user, :system or :assistant
(m/=> message [:=> [:cat [:enum :user :system :assistant] :string]
               [:map-of :keyword :string]])


(def stream-schema
  [:or
   [:tuple [:enum :role] [:enum :user :system :assistant]]
   [:tuple [:enum :content] :string]
   [:tuple [:enum :stop]]])



(defn xform-openai-res
  [{[msg] :choices}]
  (match msg
         {:delta {:content content}} [:content content]
         {:delta {:role role}} [:role role]
         :else [:end]))


(defn chat-chan
  [c]
  (let [out (chan)]
    (go-loop []
      (match (<! c)
             {:choices [msg]}
             (do
               (match msg
                      {:delta {:content content}} (>! out [:content content])
                      {:delta {:role role}} (>! out [:role role])
                      {:finish_reason reason} (>! out [:end reason]))
               (recur))
             :done (close! out)
             :else (close! out)))
    out))


(defn convo->messages
  [convo]
  (vec (for [[role content] convo]
         {:role role :content content})))


(defn messages->convo
  [messages]
  (vec (for [{:keys [role content]} messages]
         [role content])))


(defn chat
  "Uses OpenAI chat API to chat with the given text."
  ([convo & {:keys [stream model]
             :or {stream false model chat-model}}]
   (let [response (api/create-chat-completion
                   {:model model
                    :stream stream
                    :messages (convo->messages convo)
                    :max_tokens 2048
                    :temperature 0.7
                    :top_p 1
                    :frequency_penalty 0.0
                    :presence_penalty 0.0})]
     (if stream
       (chat-chan response)
       (messages->convo
        (get-in response [:choices 0 :message]))))))


(defn collect-streaming-response
  [c]
  (loop [rl nil cn ""]
    (if-let [v (<!! c)]
      (match v
             [:role role] (recur role cn)
             [:content content] (recur rl (str cn content))
             :else (recur rl cn))
      [rl cn])))
