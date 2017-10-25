(ns simba.consumer
  (:require [clojure.core.async :refer [put!]]
            [clojure.edn :as edn]

            [amazonica.aws.sqs :as sqs]
            [cemerick.bandalore :as bandalore]
            [com.climate.squeedo.sqs-consumer
             :refer [start-consumer]]
            [hara.common.error :refer [error]]

            [simba.executor :refer [process-task]]
            [simba.utils :as utils]))


(defn start [opts]

  (let [worker-def-file (:worker-definition opts)
        workers (utils/load-worker-def worker-def-file)
        valid-def? (utils/valid-worker-def? workers)

        aws-region (utils/get-region (:aws-region opts))
        input-queue-urn (:input-queue opts)
        input-queue (sqs/find-queue input-queue-urn)
        client (bandalore/create-client)

        opts' (assoc opts :workers workers)]

    (if-not valid-def?
      (error "Invalid worker definition file"))

    (if-not input-queue
      (error "No sqs queue found for input"))

    ;; Opts ok. Start consumer
    (.setRegion client aws-region)

    (start-consumer
     input-queue-urn

     (fn [task-msg done-chan]
       (let [task-body (:body task-msg)
             task (edn/read-string task-body)]

         (if-not (utils/valid-task? task)
           (error "Invalid task")

           (put! done-chan (process-task task opts')))))

     :client client)))