(ns simba.executor
  (:require [hara.expression.form :refer [form-fn]]
            [hara.function.args :refer [arg-check op]]
            [hara.common.error :refer [error suppress]]))

(defn exec [fn-form & args]
  (let [fn' (form-fn fn-form)
        args-len (count args)
        correct-args? (arg-check fn' args-len)]

    (if-not correct-args?
      (error (Exception. "Function accepts incorrect number of args")))

    ;; all good? proceed.
    (suppress (apply fn' args)
              (fn [e] (println (.getMessage e))))))