(ns Scyan.wa
  (:require [malli.core :as m]
            [hato.client :as hc]))


(def api-url "http://api.wolframalpha.com/v1/")
(def app-id (System/getenv "WOLFRAM_APP_ID"))

(defn result
  [query]
  (get-in
   (hc/get (str api-url "result")
           {:query-params {:i query
                           :appid app-id}})
   [:body]))

(m/=> result [:=> [:cat :string] :string])

(comment
  (result "2+2"))
