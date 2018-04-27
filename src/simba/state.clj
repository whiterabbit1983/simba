(ns simba.state
  (:require [clojure.spec.alpha :as spec]
            [clojure.edn :as edn]
            [hara.common.error :refer [error]]
            [taoensso.timbre :as log]
            [simba.activemq :as amq]
            [simba.schema :refer [eos-task-schema]]
            [simba.utils :refer [task->map]])
  (:import [javax.jms Session]))

(defn dispatch [q msg]
  (with-open [producer (amq/get-producer q)]
    (amq/send-message producer (pr-str msg))))

(defn get-worker-stats
  [worker]
  {:pre [(not (nil? (:queue-name worker)))
         (string? (:queue-name worker))
         (> (count (:queue-name worker)) 0)]}  
  "Get worker stats: tasks count and online status"
  (binding [amq/*session* (amq/create-session :ack-mode Session/CLIENT_ACKNOWLEDGE)]
    (let [consumer (amq/get-consumer (:queue-name worker))
          next-msg (fn [] (try (amq/receive consumer 1000) (catch NullPointerException e nil)))
          result {:task-count 0 :online? true}]
      (loop [cnt 0 msg (next-msg) online? true]
        (if-not msg
          (assoc result :task-count cnt :online? online?)
          (recur
           (+ cnt 1)
           (next-msg)
           (->> msg
                (edn/read-string)
                (task->map)
                (spec/valid? eos-task-schema)
                (not)
                (and online?))))))))

(defn get-available
  [workers]
  "Return a vector of available workers"
  (let [workers-stats (map get-worker-stats workers)
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
