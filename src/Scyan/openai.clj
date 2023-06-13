(ns Scyan.openai
  (:require [wkok.openai-clojure.api :as api]
            [malli.core :as m]
            [malli.util :as mu]
            [Scyan.super-fn :refer [summarize-convo]]
            [crypticbutter.snoop :refer [>defn =>]]
            [clojure.core.match :refer [match]]
            [clojure.core.async :refer [mult go go-loop <! >! chan close! timeout >!! <!! put! tap onto-chan!] :as a]))


(def embedding-model "text-embedding-ada-002")
(def gpt-3 "gpt-3.5-turbo")
(def gpt-4 "gpt-4")
(def chat-model gpt-3)

(def model-schema [:enum gpt-3 gpt-4])
(def default-system-prompt "You are a helpful assistant.")
(def embedding-dim 1536)
(def standard-role-map {:user :user :assistant :assistant})
(def reverse-role-map {:user :assistant :assistant :user})

(def role-schema [:enum :system :user :assistant])
(def convo-schema [:vector [:tuple role-schema :string]])


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
  ([user-prompt]
   (new-convo user-prompt default-system-prompt))
  ([user-prompt system-prompt]
   (let [convo [[:system system-prompt]]]
     (if user-prompt
       (conj convo [:user user-prompt])
       convo))))


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


(defn swap-role [role]
  (case role
    :user :assistant
    "user" "assistant"
    :assistant :user
    "assistant" "user"
    role))


(defn swap-roles [convo]
  (mapv (fn [[role content]]
          [(swap-role role) content]) convo))



(defn chat-chan
  ([c] (chat-chan c false))
  ([c swap-roles]
   (println swap-roles)
   (let [out (chan)]
     (go-loop []
       (let [res (<! c)]
         (match res
                {:choices [msg]}
                (do
                  (match msg
                         {:delta {:content content}} (>! out [:content content])
                         {:delta {:role role}} (let [role (if swap-roles (swap-role role) role)]
                                                 (>! out [:role role]))
                         {:finish_reason reason} (>! out [:end reason]))
                  (recur))
                :done (close! out)
                :else (close! out))))
     out)))

(defn convo->messages
  [convo]
  (vec (for [[role content] convo]
         {:role role :content content})))


(defn messages->convo
  [messages]
  (vec (for [{:keys [role content]} messages]
         [(keyword role) content])))


(>defn chat
  "Uses OpenAI chat API to chat with the given text."
  [convo &
   {:keys [stream model swap-roles
           max_tokens temperature top_p
           frequency_penalty presence_penalty]
    :or   {stream            false ; Returns response stream core.asyn channel
           model             chat-model ; Defaults to 3.5
           swap-roles        false ; Swaps user and assistant roles
           max_tokens        2048 ; Number of tokens allowed in prompt and response
           temperature       0.7 ; 0 for low randomness, 1 for high randomness
           top_p             1 ; Alternate to temperature (do not change both)
           frequency_penalty 0.0
           presence_penalty  0.0}}]
  ;; schema
  [convo-schema (mu/optional-keys [:map {:closed true}
                                   [:stream :boolean]
                                   [:swap-roles :boolean]
                                   [:model model-schema]
                                   [:max_tokens :int]
                                   [:temperature number?]
                                   [:top_p number?]
                                   [:frequency_penalty number?]
                                   [:presence_penalty number?]])
   => :any]
  (let [convo    (if swap-roles (swap-roles convo) convo)
        response (api/create-chat-completion
                   {:model             model
                    :stream            stream
                    :messages          (convo->messages convo)
                    :max_tokens        max_tokens
                    :temperature       temperature
                    :top_p             top_p
                    :frequency_penalty frequency_penalty
                    :presence_penalty  presence_penalty})]
    (if stream
      (chat-chan response swap-roles)
      (messages->convo
        [(get-in response [:choices 0 :message])]))))


(defn collect-streaming-response
  [c]
  (loop [rl nil cn ""]
    (if-let [v (<!! c)]
      (match v
             [:role role] (recur role cn)
             [:content content] (recur rl (str cn content))
             :else (recur rl cn))
      [rl cn])))


(defn append-streaming-response!
  [c a]
  (a/go-loop []
    (when-let [v (<! c)]
      (match v
             [:role role] (swap! a conj [(keyword role) ""])
             [:content content] (let [n (count @a)]
                                  (swap! a update-in [(dec n) 1] str content))
             :else nil)
      (recur))))


(defn convo->str
  [convo]
  (apply str
         (for [[role content] convo]
           (str "**" (name role) "**: " content "\n\n"))))


(defn compress-convo
  [convo]
  (let [target (-> convo rest butlast)
        s (convo->str target)
        compressed (summarize-convo s)]
    [(first convo)
     [:user (str "Here is our conversation thus far:\n\n" compressed)]
     (last convo)]))

(comment

  (def a (atom (new-convo "What is 10 * 11?")))
  (tap> a)
  (reset! a (new-convo "Provide example markdown including a clojure code block"))
  (let [n (count @a)
        c (chat @a :stream true)]
    (loop []
      (when-let [v (<!! c)]
        (match v
               [:role role] (swap! a conj [(keyword role) ""])
               [:content content] (swap! a update-in [n 1] str content)
               :else nil)
        (recur))))

  (def o ^{:portal.viewer/default :portal.viewer/markdown} (atom ""))
  (tap> o)
  (reset! o "")
  (let [c (chat (new-convo "What is my age if I was born in 1979?") :stream true)]
    (loop []
      (when-let [v (<!! c)]
        (match v
               [:role role] (swap! o str "\n\n# " (keyword role) "\n\n")
               [:content content] (swap! o str content)
               :else nil)
        (recur))))


  (defn fun
    [name & {:keys [greeting model] :or {greeting "Hello"
                                         model "gpt-3.5-turbo"}}]
    [:string [:map {:closed true}
              [:greeting {:optional true} :string]
              [:model {:optional true} [:enum "gpt-4" "gpt-3.5-turbo"]]]
     => :string]
    (str greeting " " name))

  (fun "Bob" :greeting "goodbye" :model "gpt-4")

  (chat [[:assistant "The high temp today is 75 degrees."]
         [:user "Thank you for letting me know. Is there anything else you need assistance with?"]])

  (chat [[:system "For this conversation, you are the user and I will play the assistant. Beegin by asking me for information related to to city of Minneapolis."]
         [:assistant "Can you provide me with some information about the city of Minneapolis?"]
         [:user "Yes, Minneapolis is the largest city in Minnesota and sister city to the state capitol of St. Paul. What other information would you like to know?"]]))

