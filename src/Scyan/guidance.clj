(ns Scyan.guidance
  (:require
   [libpython-clj2.python :refer [py. py.. py.-] :as py]
   [libpython-clj2.require :refer [require-python]]))


(require-python '[guidance :as pyguide])

(def pyguide (py/import-module "guidance"))

(py/set-attr! pyguide :llm (py. pyguide/llms OpenAI "gpt-3.5-turbo"))

(def smart-fn-prompt "You provide the tersest possible responses to user requests without repeating context.")
(def smart-fn-default-model "gpt-3.5-turbo")

(defn reset-cache! []
  (py.. pyguide/llms -OpenAI -cache clear))

(defn command
  [cmd & {:keys [sys-prompt post-fn model temperature]
          :or {sys-prompt smart-fn-prompt
               post-fn identity
               model smart-fn-default-model
               temperature 0}
          :as args}]
  (py/set-attr! pyguide :llm (py. pyguide/llms OpenAI model))
  (let [text "
 {{#system~}}
 {{sys-prompt}}
 {{~/system}}

 {{#user~}}
 {{cmd}}
  {{~/user}}

  {{#assistant~}}
  {{~gen 'answer' temperature=temperature}}
  {{~/assistant}}
  "
        llm (pyguide text :llm (py. pyguide/llms OpenAI model))]
    (->
      (llm :sys-prompt sys-prompt :cmd cmd :temperature temperature)
      (py/get-item :answer)
      post-fn)))


(defn command2
  [cmd & {:keys [sys-prompt post-fn model temperature]
          :or {sys-prompt smart-fn-prompt
               post-fn identity
               model smart-fn-default-model
               temperature 0}
          :as args}]
  (py/set-attr! pyguide :llm (py. pyguide/llms OpenAI model))
  (let [text "
 {{cmd}}

{{#geneach 'answers' num_iterations=5 join=', '}}{{gen 'answer' temperature=temperature}}{{/geneach}}
  "
        llm (pyguide text :llm (py. pyguide/llms OpenAI model))]
    (->
      (llm :sys-prompt sys-prompt :cmd cmd :temperature temperature)
      (py/get-item :answers)
      post-fn)))



(comment

  (reset-cache!)
  (command2 "Provide a list of dog breeds." :model "text-davinci-003")



  (def prompt (pyguide "Trying some {{stuff}}"))

  (prompt :stuff "Things")


  (let [people   ["John" "Mary" "Bob" "Alice"]
        ideas    [{:name "truth" :description "the state of being the case"}
                  {:name "love" :description "a strong feeling of affection"}]
        program1 (pyguide "List of people:\n{{#each people}}- {{this}}\n{{/each~}}")
        program  (pyguide "{{>program1}}
List of ideas:
{{#each ideas}}{{this.name}}: {{this.description}}
{{/each}}")]
    (program :program1 program1 :people people :ideas ideas))

  (py/run-simple-string "import guidance;print(guidance('''{{! This is a comment }}And this is not''')())")
  (let [p (pyguide "{{! This is a comment }}And this is not")]
    (p))

  ((pyguide "I just can't wait until {{~gen 'cant wait' temperature=0.7 max_tokens=7}}"))

  (py.. pyguide/llms -OpenAI -cache clear))
