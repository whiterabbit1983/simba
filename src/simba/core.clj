(ns simba.core
  (:require [simba.commands :refer [run] :rename {run cli-run}]
            [simba.consumer :refer [start]]
            [taoensso.timbre :as log])
  (:gen-class))

(defn -main [& args]
  (try
    (cli-run start args)
    (catch Throwable e (log/error (.getMessage e)))))


;; (defn -main [& args]
;;     (cli-run start args))
