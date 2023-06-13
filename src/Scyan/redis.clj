(ns Scyan.redis
  (:require [taoensso.carmine :as car :refer [wcar]]
            [Scyan.openai :refer [embedding]]))


(defonce pool (car/connection-pool {}))
(def conn-spec {:uri "redis://localhost:6379/0"})
(def opts {:pool pool :conn-spec conn-spec})

(macroexpand-1 '(wcar* (car/ping)))

(wcar opts (car/ping))

(wcar opts (car/set "Hello" "Clojure"))

(car/wcar opts (car/ping))

(wcar opts (car/set "obj" {:collection "test"
                           :contents "This is a test"
                           :meta {:a 1 :b 2}
                           :embedding (embedding "This is a test")}))

(wcar opts (car/get "obj"))

(wcar opts (car/set "keyword:1000" "blah blah blah"))

(wcar opts (car/exec "get obj"))

(car/eval)

(car/redis-call "FT.SEARCH" "vecindex"
                "*=>[KNN 3 @vec $BLOB]")
