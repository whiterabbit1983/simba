(ns simba.consumer
  (:require [clojure.core.async :refer [go >! put!]]
            [clojure.edn :as edn]

            [amazonica.aws.sqs :as sqs]
            [cemerick.bandalore :as bandalore]
            [com.climate.squeedo.sqs-consumer
             :refer [start-consumer]]
            [hara.common.error :refer [error]]

            [taoensso.timbre :as log]

            [simba.executor :refer [process-task]]
            [simba.schema :refer [task-defaults worker-defaults]]
            [simba.utils :as utils]))


(defn start [opts]

  (let [worker-def-file (:worker-definition opts)
        workers-edn (utils/load-worker-def worker-def-file)
        workers (map #(merge worker-defaults %) workers-edn)
        valid-def? (utils/valid-worker-def? workers)

        aws-region (utils/get-region (:aws-region opts))
        input-queue-urn (:input-queue opts)
        input-queue (sqs/find-queue input-queue-urn)
        client (bandalore/create-client)

        opts' (assoc opts :workers workers)]

    (if-not valid-def?
      (do
        (log/error "Invalid worker definition file")
        (error "Invalid worker definition file")))

    (if-not input-queue
      (do
        (log/error "No sqs queue found for input")
        (error "No sqs queue found for input")))

    ;; Opts ok. Start consumer
    (log/info "Setting region")
    (.setRegion client aws-region)

    (log/info "Starting consumer")

    (start-consumer
     input-queue-urn

     (fn [task-msg done-chan]

       (go
        (let [task-body (:body task-msg)
              task-edn (edn/read-string task-body)
              task (merge task-defaults task-edn)]

          (log/info "Task received")

          (if-not (utils/valid-task? task)
            (do
              (log/error "Invalid task")
              (error "Invalid task")))

          (log/info "Processing task...")
          (>! done-chan (process-task task opts')))))

     :client client)))