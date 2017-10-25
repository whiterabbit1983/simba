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

  (let [{:keys [workers secret input-queue]} opts
        {:keys [assigner payload retries timeout]} task

        dl-queue (str input-queue "-failed")

        retries-exhausted? (< retries 0)
        failed-task (assoc task
                      :nack timeout
                      :retries (dec retries))

        available-workers (state/get-available workers)
        available? (count available-workers)

        integer-assigner? (integer? assigner)
        selected-idx (if integer-assigner?
                       assigner
                       (exec assigner available-workers))

        selected (available-workers selected-idx)
        out-queue (and selected (:sqs-urn selected))

        verified? (utils/verify-task task secret)]

    (if-not verified?
      (error "Task could not be verified"))

    (cond
     retries-exhausted?
     (do
       (state/dispatch dl-queue task)
       task)

     (not available?)
     failed-task

     ;; all good? enqueue task.
     (and available? selected)
     (do
       (state/dispatch out-queue payload)
       (assoc task :status "completed")))))