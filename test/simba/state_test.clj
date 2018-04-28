(ns simba.state-test
  (:require [clojure.test :refer :all]
            [simba.state :as +s]
            [simba.activemq :as +a])
  (:import [javax.jms Session]))

(def offline-msg [{:key "type" :value "EOS"}
                  {:key "status" :value "offline"}])

(def away-msg [{:key "type" :value "EOS"}
               {:key "status" :value "away"}])

(defn mq-fixture [test-func]
  (+a/init-connection "vm://localhost?broker.persistent=true")
  (test-func)
  (try (+a/close-connection) (catch IllegalStateException e nil)))

(use-fixtures :each mq-fixture)

(deftest get-available-tests
  (with-redefs [+s/get-worker-stats (fn [_]) (throw (Exception.))]
    (is (thrown-with-msg? Exception #"No workers available" (+s/get-available [{:email "w@cc.com" :queue-name "test-q1"}]))))
  (testing "Empty list of workers"
    (is (thrown-with-msg? Exception #"No workers available" (+s/get-available []))))
  (testing "One worker provided"
    (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1"}])))
    (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 0}])))
    (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1}])
           [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :above-hwm? false}]))
    (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :hwm 0}])
           [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :hwm 0 :above-hwm? false}])))
  (testing "Multiple workers provided"
    (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                   {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
    (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                   {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
    (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 0}
                                   {:email "w2@cc.com" :queue-name "test-receiver-2" :capacity 0}])))
    (with-open [p (+a/get-producer "test-receiver-3")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 1}
                                {:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2}])
             [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2 :current 1 :above-hwm? true}])))
    (with-open [p (+a/get-producer "test-receiver-1")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (is (= (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 1}
                                {:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1}])
             [{:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1 :current 1 :above-hwm? false}]))))
  (testing "worker is offline"
    (with-open [p (+a/get-producer "queue-1")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (+a/send-message p (pr-str offline-msg))
      (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "queue-1" :capacity 1}]))))))

(deftest get-workers-stats-tests
  (testing "checking queue name"
    (is (thrown? AssertionError (+s/get-worker-stats {})))
    (is (thrown? AssertionError (+s/get-worker-stats {:queue-name 5})))
    (is (thrown? AssertionError (+s/get-worker-stats {:queue-name ""}))))
  (testing "main functionality"
    ;; q1 - does not contain EOS message, but contains others
    ;; q2 - contains various messages including one EOS
    ;; q3 - contains various messages including multiple EOS
    ;; q4 - contains only one EOS message
    ;; q5 - contains several EOS messages only
    ;; q6 - empty queue
    
    (with-open [p (+a/get-producer "q1")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (is (= (+s/get-worker-stats {:queue-name "q1"}) {:task-count 1 :online? true})))
    (with-open [p (+a/get-producer "q2")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (+a/send-message p (pr-str offline-msg))
      (is (= (+s/get-worker-stats {:queue-name "q2"}) {:task-count 2 :online? false})))
    (with-open [p (+a/get-producer "q3")]
      (+a/send-message p (pr-str [{:key "a" :value "b"}]))
      (+a/send-message p (pr-str offline-msg))
      (+a/send-message p (pr-str away-msg))
      (is (= (+s/get-worker-stats {:queue-name "q3"}) {:task-count 3 :online? false})))
    (with-open [p (+a/get-producer "q4")]
      (+a/send-message p (pr-str offline-msg))
      (is (= (+s/get-worker-stats {:queue-name "q4"}) {:task-count 1 :online? false})))
    (with-open [p (+a/get-producer "q5")]
      (+a/send-message p (pr-str offline-msg))
      (+a/send-message p (pr-str away-msg))
      (is (= (+s/get-worker-stats {:queue-name "q5"}) {:task-count 2 :online? false}))
      ;; run sedond time to make sure messages can be retrieved again
      (is (= (+s/get-worker-stats {:queue-name "q5"}) {:task-count 2 :online? false})))
    (with-open [p (+a/get-producer "q5")]
      (is (= (+s/get-worker-stats {:queue-name "q6"}) {:task-count 0 :online? true})))
    ;; no queue
    (is (= (+s/get-worker-stats {:queue-name "non_existent_one"}) {:task-count 0 :online? true}))))
