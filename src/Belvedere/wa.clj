(ns Belvedere.wa
  (:require [martian.core :as martian]
            [martian.hato :as http]
            [malli.core :as m]
            [schema.core :as s]
            [hato.client :as hc]
            [clojure.repl :refer [doc dir]]))


(def api-url "http://api.wolframalpha.com/v1")
(def app-id (System/getenv "WOLFRAM_APP_ID"))


(def api
  (http/bootstrap api-url
                  [{:route-name :short-answer
                    :path-parts ["/spoken/"]
                    :query-schema {:i s/Str
                                   :appid s/Str}
                    :method :get}]))

(comment

  (hc/get api-url {:query-params {:i "5 * 5" :appid app-id}})

  (martian/explore api :short-answer)
  (martian/url-for api :short-answer {:i "What is the meaning of life?"
                                      :appid app-id})

  (martian/request-for api :short-answer {:i "5 * 5"
                                          :appid app-id})

  (martian/response-for api :short-answer {:i "5 * 5"
                                           :appid app-id})


  (defn query [input]
    (let [params {:i input
                  :appid app-id}]
      (-> (http/get api-url)
          (http/with-query-params params)
          (http/execute!)
          (http/response-body))))

  (defn -main []
    (let [input "What is the meaning of life?"]
      (println (wolfram-alpha-query input)))))
