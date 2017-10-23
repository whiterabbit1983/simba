(ns simba.consumer
  (:require [clojure.core.async :refer [put!]]

            [amazonica.aws.sqs :as sqs]
            [com.climate.squeedo.sqs-consumer
             :refer [start-consumer]]
            [hara.common.error :refer [error]]

            [simba.executor :refer [process-task]]
            [simba.utils :as utils]))

(defn start [opts]

  (let [worker-def-file (:worker-definition opts)
        workers (utils/load-worker-def worker-def-file)
        valid-def? (utils/valid-worker-def? workers)

        input-queue-urn (:input-queue opts)
        input-queue (sqs/find-queue input-queue-urn)

        opts' (assoc opts :workers workers)]

    (if-not valid-def?
      (error "Invalid worker definition file"))

    (if-not input-queue
      (error "No sqs queue found for input"))

    ;; Opts ok. Start consumer
    (start-consumer
     input-queue-urn

     (fn [task done-chan]
       (if-not (utils/valid-task? task)
         (error "Invalid task")

         (put! done-chan (process-task task opts')))))))