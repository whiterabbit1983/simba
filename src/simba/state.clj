(ns simba.state
  (:require [clojure.spec.alpha :as spec]
            [clojure.edn :as edn]
            [hara.common.error :refer [error]]
            [taoensso.timbre :as log]
            [simba.rabbitmq :as rmq]
            [simba.schema :refer [eos-task-schema]]
            [simba.utils :refer [task->map]]
            [simba.settings :as +s]
            [clj-time.core :as tcore]
            [clj-time.coerce :as tcoerce])
  (:import [javax.jms Session]))

(defn dispatch [q msg]
  (with-open [producer (rmq/get-channel q)]
    (let [timestamp (tcoerce/to-long (tcore/now))
          labeled-msg (conj msg {:key "timestamp" :value (str timestamp)})]
      (log/debug (str "Sending message " labeled-msg))
      (rmq/send-message producer (pr-str labeled-msg)))))

(defn get-worker-stats
  "Get worker's task count and availability"
  [worker]
  {:pre [(not (nil? (:queue-name worker)))
         (string? (:queue-name worker))
         (> (count (:queue-name worker)) 0)]}
  (with-open [eos_consumer (rmq/get-channel (str +s/eos-queue-prefix (:queue-name worker)))
              task_counter (rmq/get-channel (str +s/task-queue-prefix (:queue-name worker)))]
    {:task-count (rmq/message-count task_counter)
     :online? (= (rmq/message-count eos_consumer) 0)}))

(defn get-available
  [workers]
  "Return a vector of available workers"
  (let [workers-stats (->> workers
                           (map #(try
                                   (get-worker-stats %)
                                   (catch Exception e (log/error (str e)))))
                           (filter identity))
        ;; TODO: refactor the next two lines!
        current-state (into [] (map #(:task-count %) workers-stats))
        online-state (into [] (map #(:online? %) workers-stats))]

    (if (empty? current-state)
      (error "No workers available"))

    (into [] (->>
              workers
              (map-indexed
               (fn [i worker]
                 (let [current (get current-state i)
                       worker-online? (get online-state i)
                       capacity (or (:capacity worker) 0)
                       hwm (or (:hwm worker) 0)
                       above-hwm? (> current hwm)
                       available? (and worker-online? (> capacity current))]

                   (log/info "Getting current state")
                   (and available?
                        (assoc worker
                          :current current
                          :above-hwm? above-hwm?)))))
              (filter identity)))))
