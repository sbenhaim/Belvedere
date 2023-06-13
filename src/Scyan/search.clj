(ns Scyan.search
  (:require [malli.core :as m]
            [hato.client :as hc]
            [clojure.string :as str]))


(def api-url "https://customsearch.googleapis.com/customsearch/v1")
(defonce cx "3469c8a5a1da64c40")
(defonce search-key (System/getenv "GOOGLE_KEY"))

(defn query
  ([q] (query q nil))
  ([q n]
   (let [matches (get-in
                  (hc/get api-url
                          {:as :json
                           :query-params {:cx cx
                                          :key search-key
                                          :q q}})
                  [:body :items])]
     (if n (take n matches) matches))))

(defn format-response
  [res]
  (str/join "\n\n"
   (for [[title link snippet] (mapv (juxt :title :link :snippet) res)]
     (str "## " title "\n" link "\n" snippet "\n"))))


(defn search-for [q n]
  (format-response (query q n)))




(comment
  (user/text-tap>
   (format-response
    (query "Lakehouse design best practices." 5)))
  )
