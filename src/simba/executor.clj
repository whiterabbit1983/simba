(ns simba.executor
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :refer [go >! put!]]
            [hara.expression.form :refer [form-fn]]
            [hara.function.args :refer [arg-check op]]
            [hara.common.error :refer [error suppress]]
            [taoensso.timbre :as log]
            [simba.state :as state]
            [simba.utils :as utils]
            [simba.settings :as +s]))

(defn exec [fn-form & args]

  (let [fn' (form-fn fn-form)
        args-len (count args)
        correct-args? (suppress (arg-check fn' args-len))]

    (if-not correct-args?
      (error "Function accepts incorrect number of args"))

    ;; all good? proceed.
    (log/info "Executing assigner")
    (suppress (apply fn' args)
              (fn [e] (log/error (.getMessage e))))))


;; specs to validate process-task's arguments
(s/def ::task-arg (s/keys :req-un [::assigner ::payload ::retries ::timeout]))
(s/def ::opts-arg (s/keys :req-un [::workers ::secret ::input-queue]))


(defn process-task [task opts]
  {:pre [(s/valid? ::task-arg task) (s/valid? ::opts-arg opts)]}  
  "Process a task"
  (let [{:keys [workers secret input-queue]} opts
        {:keys [assigner payload retries timeout]} task

        dl-queue (str +s/task-queue-prefix input-queue "-failed")

        retries-exhausted? (< retries 0)
        failed-task (assoc task
                      :nack timeout
                      :retries (dec retries))

        available-workers (state/get-available workers)
        available? (> (count available-workers) 0)

        integer-assigner? (integer? assigner)
        selected-idx (if integer-assigner?
                       assigner
                       (exec assigner available-workers))

        selected (get available-workers selected-idx)
        out-queue (and selected (str +s/task-queue-prefix (:queue-name selected)))
        verified? (utils/verify-task task secret)]

    (if-not verified?
      (error "Task could not be verified"))

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
     (do
       (log/warn "Processing failed")
       task))))
