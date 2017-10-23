(ns simba.state
  (:require [amazonica.aws.sqs :as sqs]
            [hara.common.error :refer [error]]))

(defn dispatch [q msg]
  (-> q
      sqs/find-queue
      (sqs/send-message (pr-str msg))))

(defn get-attrs [q]
  (sqs/get-queue-attributes q ["All"]))

(defn get-capacity [q]
  (-> q get-attrs :ApproximateNumberOfMessages))

(defn get-available [workers]

  (let [q-urns (map :sqs-urn workers)
        qs (map sqs/find-queue q-urns)
        all-found? (empty? (filter nil? qs))
        current-state (and all-found? (map get-capacity qs))]

    (if-not all-found?
      (error "Worker queues not found"))

    (remove nil? (->> workers (map-indexed
     (fn [i worker]
       (let [current (current-state i)
             capacity (:capacity worker)
             hwm (:hwm worker)
             above-hwm? (> current hwm)
             available? (> capacity current)]

         (and available?
              (assoc worker
                :current current
                :above-hwm? above-hwm?)))))))))
