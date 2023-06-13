(ns Scyan.wa
  (:require [malli.core :as m]
            [hato.client :as hc]))


(def api-url "http://api.wolframalpha.com/v1/")
(defonce app-id (System/getenv "WOLFRAM_APP_ID"))

(defn query
  [q]
  (get-in
   (hc/get (str api-url #_"result" #_"conversation.jsp" "llm-api")
           {:query-params {:input q
                           :appid app-id}})
   [:body]))


(comment
  (query "are 3, 5, 7 ,8 coprime?")
  (query "2+2"))

