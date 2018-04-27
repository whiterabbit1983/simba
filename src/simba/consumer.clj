(ns simba.consumer
  (:require [clojure.core.async :refer [go >! put! <!!]]
            [clojure.spec.alpha :as spec]
            [clojure.edn :as edn]
            [amazonica.aws.sqs :as sqs]
            [cemerick.bandalore :as bandalore]
            [com.climate.squeedo.sqs-consumer
             :refer [start-consumer]]
            [taoensso.timbre :as log]
            [pinpointer.core :as p]
            [hara.common.error :refer [error]]
            [simba.executor :refer [process-task]]
            [simba.schema :refer [task-defaults worker-defaults
                                  workers-schema task-schema]]
            [simba.utils :as utils]))


(defn start [opts]

  (let [worker-def-file (:worker-definition opts)
        workers-edn (utils/yaml->map worker-def-file)
        workers (map #(merge worker-defaults %) workers-edn)
        valid-def? (spec/valid? workers-schema workers)
        aws-region (utils/get-region (:aws-region opts))
        input-queue-urn (:input-queue opts)
        input-queue (sqs/find-queue input-queue-urn)
        client (bandalore/create-client)
        opts' (assoc opts :workers workers)]
    (if-not valid-def?
      (do
        (log/info (p/pinpoint workers-schema workers))
        (error "Invalid worker definition file")))

    (if-not input-queue
      (error "No sqs queue found for input"))

    ;; Opts ok. Start consumer
    (log/info (str "Setting region to " aws-region))
    (.setRegion client aws-region)

    (log/info "Starting consumer")

    (start-consumer
       input-queue-urn
       ;; TODO: refactor try...catch stuff in a macro
       (fn [task-msg done-chan]
         (let [ret-chan (go
                          (try (let [task-body (:body task-msg)
                                     task-edn (edn/read-string task-body)
                                     task-valid? (spec/valid? task-schema task-edn)
                                     validated-task (if task-valid? task-edn {})
                                     task (merge task-defaults validated-task)]

                                 (log/info "Task received")

                                 (if-not task-valid?
                                   (do
                                     (log/info (p/pinpoint task-schema task))
                                     (error (str "Invalid task " task))))

                                 (log/info "Processing task...")
                                 (let [processed-body (process-task task opts')]
                                   (-> task-msg
                                       (assoc :nack (:nack processed-body))
                                       (assoc :retries (:retries processed-body))
                                       (assoc :body processed-body)
                                       (>! done-chan))))
                               (catch Throwable e (>! done-chan task-msg))))
               ret-val (<!! ret-chan)]
           (if-not (instance? Throwable ret-val)
             ret-val
             (do
               (.printStackTrace ret-val)
               (log/error (.getMessage ret-val))))))

       :client client)))
