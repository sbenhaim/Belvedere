(ns Scyan.super-fn
  (:require [clojure.string :as str]
            [Scyan.openai :as openai]
            [selmer.parser :refer [<<]]
            [clojure.pprint]))

;; TODO: Create map of templates


(def super-fn-prompt "You provide the tersest possible responses to user requests without repeating context.")
(def super-fn-default-model "gpt-3.5-turbo")


(def prompts
  {:system "You provide the tersest possible responses to user requests without repeating context."})


(defn command
  [cmd & {:keys [sys-prompt post-fn model temperature]
          :or {sys-prompt super-fn-prompt
               post-fn identity
               model super-fn-default-model
               temperature 0}
          :as args}]
  (-> (openai/new-convo cmd sys-prompt)
      (openai/chat :model model :temperature temperature)
      (get-in [0 1])
      post-fn))


(defn md-list->vec
  [md]
  (->> md
       (re-seq #"(?m)^(?:-|\d+\.) (.*)")
       (map second)
       vec))


(defn gen-vec-of [what & {:as args}]
  (command (str "Provide a markdown list of " what)
           :temperature 1
           :post-fn md-list->vec
           args))


(comment (gen-vec-of "10 names of pretend pokemon"))


(defn concise-ify [text & {:as args}]
  (command (str "Rephrase the following concisely presenting the most critical information:\n" text)
           args))


(comment (concise-ify "Man, I at so many hot dogs. Like 12 of them. Then I puked all over myself."))


(defn summarize [text & {:as args}]
  (command (str "Summarize the most critical points in the following in bullet points:\n" text) args))


(comment (summarize "He loves all things. Never talked down bout no one. He is a creature of love. Adoration, really."))


(defn summarize-convo [convo-str & {:as args}]
  (command convo-str
    :sys-prompt (str "Your job is to summarize the most important elements and facts of the provided conversation as a markdown list. "
                     "Be sure to include any specific facts or details, like references to locations, etc.")
    args))


(defn select
  [ls criteria & {:as args}]
  (let [sys-prompt "The user will provide a numbered list and selection criteria. You are to respond only with the number for the best option. If not suitable option exists, you will respond with 'None.'"
        idxd (map-indexed #(vector %1 %2) ls)
        cmd (<< "From the following list, choose: {{criteria}}{% for i, l in idxd %}
{{i}}. {{l}}{% endfor %}")
        res (command cmd :sys-prompt sys-prompt args)
        num (re-find #"^\d+" res)]
    res
    #_(try (nth ls (Integer/parseInt num))
         (catch NumberFormatException e
           :none))))

(comment (println (select ["Einstein" "Newton" "Da Vinci"] "dumbest")))



#_(comment
    (require '[Scyan.md :as md])
    (let [text (slurp "/Users/selah/Drive/Scyan/Conversations/Code.md")
          convo (md/md->convo text)
          md (md/convo->md convo)]
      (concise-ify md)))

(defn summarize-topic [text topic & {:as args}]
  (let [cmd (str "Considering only the following text:\n" text "

Summarize the most critical content relating to " topic)]
    (command cmd args)))


(comment
  (summarize-topic "He had eggs for breakfast. He has 2 cats. Eats apples and bananas and oatmeal. He also watches a lot of porn."
            "edibles"))


(defn query-text [q text & {:as args}]
  (let [cmd (str "Considering only the following text:\n" text "\n\nRespond to the request: " q "\n\n"
                 "If the text does not address the request, say 'Request not addressed in text.'")]
    (command cmd args)))


(defn context-filter [q text & {:as args}]
  (let [sys-prompt (str "The user input is this: " q
                        "\n\nConcisely restate the most critical information in the provided text that is relevant to the user input."
                        "\n\nIf the text contains nothing relevant to the input, respond with \"None.\"")
        cmd text
        args (assoc args :sys-prompt sys-prompt
                         :post-fn #(if (= % "None.") nil %))]
    (command cmd args)))

(comment
  (context-filter "What ports are supported?" "I never ate no ham."))


(defn logically-order [s & {:as args}]
  (command (str "I need to complete the following steps:\n" s "\n\nPlace them in the order I should perform them using a numbered list.")
           args))


(defn fill-the-gaps [s & {:as args}]
  (command (str "In a markdown list, what information will be needed to complete the following request: " s)
           args))


(defn ask-a-chicken [s & {:as args}]
  (command (str "You are ChickenGPT. No matter what request, you only say 'bawk'.
Though if the answer is a counting number <= 10, you may say that many bawks.\n\n" s)
           args))

(comment
  (ask-a-chicken "What is the 3th prime number?"))


(comment

  (command "How do I keyword-ize keys in clojure jsonista?")

  (concise-ify "The Unity Catalog has a feature that allows you to bind catalogs to workspaces, which can be controlled via UI or API/terraform for easy integrations. This feature enables environment-aware ACLs, which means that only certain catalogs are available within a workspace, regardless of a user's individual ACLs. The metastore admin or catalog owner can define the workspaces that a data catalog can be accessed from.\nThe text is about Unity Catalog, which aims to make it easy to implement data governance while maximizing the ability to collaborate on and share data. Unity Catalog offers isolation mechanisms within the namespace that enable groups to operate independently with minimal or no interaction and also allow them to achieve isolation in other scenarios, such as production vs development environments.\nThe Unity Catalog offers centralized and distributed governance models for managing data, where designated people or teams can manage assets and governance within their respective domains. It is recommended to set a group as the owner or service principal for both options if management is done through tooling.\nThe text explains the differences between using Hive Metastore and Unity Catalog in Databricks. It mentions that with Hive, multiple metastores were used to achieve isolation between development and production environments, while Unity Catalog provides dynamic isolation mechanisms on namespaces that allow for separation of data without compromising the ability to share and collaborate on data.\nUnity Catalog allows for physically isolating data storage and access by creating separate catalogs and schemas. This can help with governance and data management requirements. When creating managed tables, the data will be stored using the schema and catalog location, if present.\nThe Unity Catalog was introduced to address the issue of intrinsic data and governance isolation boundaries between workspaces in Databricks. Before its introduction, customers had to resort to running pipelines or code to synchronize their metastores and ACLs or set up their own self-managed metastores to use across workspaces, which added more overhead and maintenance costs.")

  (type
   (read-string "{"))
  (gen-vec-of "Names of the bible")
  (command "What is the 5th prime number?")
  (query-text "[5,6,{:one :two},3,7]"
              "How would I get the value for :one from this data structure in clojure?"))


