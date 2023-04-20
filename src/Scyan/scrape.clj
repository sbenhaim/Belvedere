(ns Scyan.scrape
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.-] :as py]))


; run wget shell command
;
(defn scrape
  ([basedir] (scrape basedir []))
  ([basedir extensions]
   (let [basedir (str basedir "/")
         extensions (if (empty? extensions)
                      ["jpg" "png" "gif"]
                      extensions)
         extensions (str/join "," extensions)
         cmd (str "wget -r -l 1 -A " extensions " " basedir)]
     (py.. (py/import-module "subprocess") "call" [cmd]))))
