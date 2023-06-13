(ns Scyan.scy-mem
  (:require [Scyan.docs :as docs]
            [clojure.core.matrix.stats :refer [cosine-similarity]]
            [clojure.pprint :refer [pprint]]))


(def index (atom {}))
(def save-path "db/")


(defn search-index [idx query-vec k]
  (->> idx
       (mapv (fn [m] (assoc m :similarity (cosine-similarity (:embedding m) query-vec))))
       (sort-by :similarity)
       reverse
       (take k)
       (mapv #(select-keys % [:similarity :contents]))))


(defn persist! [path obj]
  (spit path obj))



(defrecord MemoryIndex [collection]
  docs/Index
  (is-empty? [_] (not (seq (collection @index))))
  (insert! [_ doc] (swap! index update-in [collection] conj doc))
  (insert-many! [_ docs] (do (swap! index update-in [collection] into docs)
                             true))
  (top-n [_ query-vec k] (search-index (@index collection) query-vec k))
  (save! [_] (persist! (str save-path (name collection) ".edn") @index)))

(extend-type clojure.lang.IAtom
  docs/Index
  (is-empty? [index] (empty? @index))
  (insert! [index doc] (do (swap! index conj doc) true))
  (insert-many! [index docs] (do (swap! index into docs)
                             true))
  (top-n [index query-vec k] (search-index @index query-vec k))
  (save! [index path] (persist! path @index)))


(comment

  (require '[Scyan.openai :refer [embedding]])

  (def tmp (atom []))

  (docs/is-empty? tmp)
  (docs/insert! tmp {:contents "Hello world!" :embedding (embedding "Hello world!")})
  (docs/insert-many! tmp [{:contents "Goodbye cruel world." :embedding (embedding "Goodbye cruel world.")}
                          {:contents "Hello, you!" :embedding (embedding "Hello, you!")}])


  (docs/save! tmp "db/tmp.edn")

  (let [contents ""]
    (docs/insert-many! idx [{:contents contents
                             :embedding (embedding contents)}]))


  (def v (embedding "Do I have anything going on at 3p today?"))
  (docs/top-n idx v 2)

  (docs/save! idx)

  @index)
