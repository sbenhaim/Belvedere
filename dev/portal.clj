(ns portal
  (:require [Scyan.md :as md]))


(defn text-tap> [stuff]
  (tap> (with-meta [:portal.viewer/markdown stuff]
          {:portal.viewer/default :portal.viewer/hiccup})))


(defn tap-convo> [convo]
  (-> convo md/convo->md text-tap>))


(comment
  (def a (atom [[:system "## Hi"]
                [:user "### Bye!"]]))
  (tap-convo> a))
