(ns simba.executor
  (:require [hara.expression.form :refer [form-fn]]
            [hara.function.args :refer [arg-check op]]
            [hara.common.error :refer [error suppress]]

            [simba.state :as state]
            [simba.utils :as utils]))

(defn exec [fn-form & args]

  (let [fn' (form-fn fn-form)
        args-len (count args)
        correct-args? (arg-check fn' args-len)]

    (if-not correct-args?
      (error (Exception. "Function accepts incorrect number of args")))

    ;; all good? proceed.
    (suppress (apply fn' args)
              (fn [e] (println (.getMessage e))))))

(defn process-task [task opts]

  (let [workers (:workers opts)
        secret (:secret opts)
        assigner (:assigner task)
        payload (:payload task)
        retries (:retries task)
        timeout (:timeout task)

        failed-task (assoc task
                      :nack timeout
                      :retries (dec retries))

        available-workers (state/get-available workers)
        available? (count available-workers)

        assigner-valid? (utils/valid-function? assigner)
        selected (and assigner-valid? (exec assigner available-workers))
        out-queue (and selected (:sqs-urn selected))

        verified? (utils/verify-task task secret)]

    (if-not verified?
      (error "Task could not be verified"))

    (if-not assigner-valid?
      (error "Invalid assigner"))

    (if-not (and available? selected)
      failed-task

      ;; all good? enqueue task.
      (do
        (state/dispatch out-queue payload)
        (assoc task :status "completed")))))