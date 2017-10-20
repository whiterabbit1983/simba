(ns simba.core
  (:require [simba.commands :refer [run] :rename {run cli-run}]))

(defn -main [& args]
  (cli-run println args))