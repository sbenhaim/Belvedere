(ns user
  (:require
   [portal.api :as portal]
   [clojure.tools.namespace.repl :as namespace]))

(println "Set REPL refresh directories to " (namespace/set-refresh-dirs "src" "resources"))

(def portal-instance
  (or (first (portal/sessions))
      (portal/open {:portal.colors/theme :portal.colors/material-ui})))


(add-tap #'portal/submit)

(comment

  (portal/repl portal-instance)

  (ns user
    (:require
     [portal.ui.api :as p]
     [portal.ui.inspector :as ins]
     [reagent.core :as r]))


  (defn view-convo []
    (fn [convo]
      (for [[role txt] convo]
        [:div
         [:h1 (name role)]
         [:portal.viewer/markdown txt]])))

  (defn view-convo []
    (fn [convo]
      [:viewer/markdown "# Hello"]))

[[:strong "One"] ^{:portal.viewer/default :portal.viewer/hiccup} [:portal.viewer/markdown "## Two"]]

  (tap>
    "# Hello")

  (def role-schema [:enum :system :user :assistant])
  (def convo-schema [:vector [:tuple role-schema :string]])


  (defn convo? [c]
    (vector? c))

  (portal.ui.api/register-viewer!
    {:name ::convo
     :predicate vector?
     :component view-convo})

  (require '[clojure.tools.deps.alpha.repl :refer [add-libs]])
  (add-libs {reagent/reagent {:mvn/version "1.2.0"}})
  )
