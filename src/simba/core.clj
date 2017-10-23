(ns simba.core
  (:require [simba.commands :refer [run] :rename {run cli-run}]
            [simba.consumer :refer [start]]))

(defn -main [& args]
  (cli-run start args))