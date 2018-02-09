(ns simba.state
  (:require [amazonica.aws.sqs :as sqs]
            [hara.common.error :refer [error]]
            [taoensso.timbre :as log]))

(defn dispatch [q msg]
  (-> q
      sqs/find-queue
      (sqs/send-message (pr-str msg))))

(defn get-attrs [q]
  (sqs/get-queue-attributes q ["All"]))

(defn get-capacity [q]
  (let [attrs (get-attrs q)
        key :ApproximateNumberOfMessages
        capacity (key attrs)]
  (log/debug "Capacity: " capacity)
  (try
    (read-string capacity)
    (catch Throwable e
      (do
        (log/warn (str q ": capacity value could not be converted to integer"))
        0)))))

(defn get-available [workers]
  "Return a vector of available workers"
  (let [q-urns (map :sqs-urn workers)
        qs (map #(try
                   (sqs/find-queue %)
                   (catch Throwable e nil)) q-urns)
        current-state (into [] (->>
                                qs
                                (filter identity)
                                (map get-capacity)))]

    (if (empty? current-state)
      (error "Worker queues not found"))

    (into [] (->>
              workers
              (map-indexed
               (fn [i worker]
                 (let [current (get current-state i)
                       capacity (or (:capacity worker) 0)
                       hwm (or (:hwm worker) 0)
                       above-hwm? (> current hwm)
                       available? (> capacity current)]

                   (log/info "Getting current state")
                   (and available?
                        (assoc worker
                          :current current
                          :above-hwm? above-hwm?)))))
              (filter identity)))))
