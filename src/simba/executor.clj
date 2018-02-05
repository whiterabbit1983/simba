(ns simba.executor
  (:require [clojure.core.async :refer [go >! put!]]

            [hara.expression.form :refer [form-fn]]
            [hara.function.args :refer [arg-check op]]
            [hara.common.error :refer [error suppress]]

            [taoensso.timbre :as log]

            [simba.state :as state]
            [simba.utils :as utils]))

(defn exec [fn-form & args]

  (let [fn' (form-fn fn-form)
        args-len (count args)
        correct-args? (arg-check fn' args-len)]

    (if-not correct-args?
      (error (Exception. "Function accepts incorrect number of args")))

    ;; all good? proceed.
    (log/info "Executing assigner")
    (suppress (apply fn' args)
              (fn [e] (log/error (.getMessage e))))))

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

        selected (get available-workers selected-idx)
        out-queue (and selected (:sqs-urn selected))
        verified? (utils/verify-task task secret)]

    (if-not verified?
      (do
        (log/error "Task could not be verified")
        (error "Task could not be verified")))

    (cond
     retries-exhausted?
     (do
       (log/warn "Task retries exhausted, sent to dlq")
       (state/dispatch dl-queue task)
       task)

     (not available?)
     (do
       (log/warn "No worker available, re-queuing with timeout")
       failed-task)

     ;; all good? enqueue task.
     (and available? selected)
     (do
       (log/info "Worker matched! Dispatching")
       (state/dispatch out-queue payload)
       (assoc task :status "completed"))

     :else
     (log/warn "Processing failed"))))
