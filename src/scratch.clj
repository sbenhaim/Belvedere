(ns user
  (:require [Belvedere.load :as load]
            [Belvedere.supabase :as supabase]
            [Belvedere.docsearch :as ds]
            [portal.api :as portal]))


(def p (portal/open))
(add-tap #'portal/submit)

(defn text-tap> [stuff]
  (tap> (with-meta [:portal.viewer/markdown stuff]
          {:portal.viewer/default :portal.viewer/hiccup})))


(tap> "# Hello")
(text-tap> "# Hello")


(comment
  (ds/process-file "malli" "/Users/selah/code/docs/malli/malli.md")
  (text-tap> (ds/query-docs "malli" "How do I use the => operator?"))
  )
