(ns simba.consumer
  (:require [clojure.core.async :refer [go >! put! <!!]]
            [clojure.edn :as edn]
            [amazonica.aws.sqs :as sqs]
            [cemerick.bandalore :as bandalore]
            [com.climate.squeedo.sqs-consumer
             :refer [start-consumer]]
            [taoensso.timbre :as log]
            [hara.common.error :refer [error]]
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
      (error "Invalid worker definition file"))

    (if-not input-queue
      (error "No sqs queue found for input"))

    ;; Opts ok. Start consumer
    (log/info "Setting region")
    (.setRegion client aws-region)

    (log/info "Starting consumer")

    (start-consumer
       input-queue-urn
       ;; TODO: refactor try...catch stuff in a macro
       (fn [task-msg done-chan]
         (let [ret-chan (go
                          (try (let [task-body (:body task-msg)
                                     task-edn (edn/read-string task-body)
                                     validated-task (if (utils/valid-task? task-edn) task-edn {})
                                     task (merge task-defaults validated-task)]

                                 (log/info "Task received")

                                 (if-not (utils/valid-task? task)
                                   (error (str "Invalid task " task)))

                                 (log/info "Processing task...")
                                 (>! done-chan (process-task task opts')))
                               (catch Throwable e e)))
               ret-val (<!! ret-chan)]           
           (if-not (instance? Throwable ret-val)
             ret-val
             (do
               (.printStackTrace ret-val)
               (log/error (.getMessage ret-val))))))

       :client client)))
