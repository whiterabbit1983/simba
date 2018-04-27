(ns simba.schema
  (:require [spec-tools.data-spec :as spec]
            [spec-tools.spec :as st]
            [clojure.spec.alpha :as s]
            [clojure.set :refer [subset?]]

            [simba.constants :as constants]))

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
   :queue-name string?
   (spec/opt :rank) st/pos-int?
   (spec/opt :capacity) integer?
   (spec/opt :hwm) st/pos-int?
   (spec/opt :teams) [team]
   (spec/opt :attributes) [key-value]})

(def worker-defaults
  {:name ""
   :rank 1
   :capacity 1
   :hwm 100000
   :teams []
   :attributes []})

(def worker-schema
  (spec/spec ::worker worker))

(def workers-schema
  (spec/spec ::workers [worker]))

;; Valid assigner
(defn valid-assigner? [f]
  (let [flat-f (flatten f)
        f-syms (set (filter symbol? flat-f))
        valid? (subset? f-syms constants/allowed-symbols)]

    (and (not (nil? f)) valid?)))

;; Task schema
(def assigner
  (s/or
   :num integer?
   :fn (s/and coll? valid-assigner?)))

(def task
  {:id string?
   :nonce string?
   :created-at timestamp
   (spec/opt :retries) st/pos-int?
   (spec/opt :timeout) st/pos-int?
   :payload [key-value]
   :assigner assigner})

(def task-defaults
  {:retries 3
   :timeout (* 15 60)})

(def task-schema
  (spec/spec ::task task))

(defn eos-string?
  [s]
  (and (string? s) (= s "EOS")))

(def eos-task
  {:type eos-string?
   (spec/opt :status) string?})

(def eos-task-schema
  (spec/spec ::eos-task eos-task))
