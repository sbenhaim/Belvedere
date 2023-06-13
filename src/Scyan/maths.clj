(ns Scyan.maths
  (:require [clojure.core.matrix.stats :refer [cosine-similarity]]
            [Scyan.openai :as openai]))

(def vec1 (openai/embedding "I'm going to school."))
(def vec2 (openai/embedding "I'm going to university."))
(def vec3 (openai/embedding "I'm going to work."))

(time
 (cosine-similarity vec1 vec2))
