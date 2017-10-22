(ns simba.schema
  (:require [spec-tools.data-spec :as spec]
            [spec-tools.spec :as st]
            [clojure.spec.alpha :as s]
            [simba.utils :as u]))

;; Timestamp
(def timestamp st/pos-int?)

;; Key-value schema
(def key-value
  {:key string?
   :value string?})

;; Team schema
(def team
  {:id string?
   :name string?})

;; Worker schema
(def worker
  {(spec/opt :name) string?
   :email string?
   :sqs-urn string?
   (spec/opt :rank) st/pos-int?
   (spec/opt :capacity) st/pos-int?
   (spec/opt :hwm) st/pos-int?
   (spec/opt :teams) [team]
   (spec/opt :attributes) [key-value]})

(def worker-schema
  (spec/spec ::worker worker))

(def workers-schema
  (spec/spec ::workers [worker]))

;; Task schema
(def assigner
  (s/or
   :num st/pos-int?
   :fn (s/and coll? u/valid-function?)))

(def task
  {:id string?
   :nonce string?
   :created-at timestamp
   (spec/opt :retries) st/pos-int?
   (spec/opt :timeout) st/pos-int?
   :payload [key-value]
   :assigner assigner})

(def task-schema
  (spec/spec ::worker worker))
