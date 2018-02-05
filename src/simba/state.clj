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
  (let [attrs (q get-attrs)
        key :ApproximateNumberOfMessages
        capacity (key q)]

    (log/debug "Capacity: " (str capacity))
    capacity))

(defn get-available [workers]

  (let [q-urns (map :sqs-urn workers)
        qs (map sqs/find-queue q-urns)
        all-found? (empty? (filter nil? qs))
        current-state (and all-found? (map get-capacity qs))]

    (if-not all-found?
      (error "Worker queues not found"))

    (remove nil? (->> workers (map-indexed
     (fn [i worker]
       (let [current (get current-state i)
             capacity (:capacity worker)
             hwm (:hwm worker)
             above-hwm? (> current hwm)
             available? (> capacity current)]

         (log/info "Getting current state")
         (and available?
              (assoc worker
                :current current
                :above-hwm? above-hwm?)))))))))
