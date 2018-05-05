(ns simba.state-test
  (:require [clojure.test :refer :all]
            [simba.state :as +s]
            [simba.rabbitmq :as +r])
  (:import [javax.jms Session]))

(def offline-msg [{:key "type" :value "EOS"}
                  {:key "status" :value "offline"}])

(def away-msg [{:key "type" :value "EOS"}
               {:key "status" :value "away"}])

(deftype ChannelMock [] +r/Closeable (close [this] nil))
(def ch-mock (->ChannelMock))

(deftest get-available-tests
  (with-redefs [+s/get-worker-stats (fn [_]) (throw (Exception.))]
    (is (thrown-with-msg? Exception #"No workers available" (+s/get-available [{:email "w@cc.com" :queue-name "test-q1"}]))))
  (testing "Empty list of workers"
    (is (thrown-with-msg? Exception #"No workers available" (+s/get-available []))))
  (with-redefs [+r/get-channel (fn [_] ch-mock)]
    (testing "One worker provided"
      (with-redefs [+r/messages-seq (fn [_] [])]
        (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1"}])))
        (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 0}])))
        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1}])
               [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :above-hwm? false}]))
        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :hwm 0}])
               [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :hwm 0 :above-hwm? false}]))))
    (testing "Multiple workers provided"
      (with-redefs [+r/messages-seq (fn [_] [])]
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 0}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2" :capacity 0}]))))
      (with-redefs [+r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}])])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 1}
                                  {:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2}])
               [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2 :current 1 :above-hwm? true}])))
      (with-redefs [+r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}])])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        (is (= (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 1}
                                  {:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1}])
               [{:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1 :current 1 :above-hwm? false}]))))
    (testing "worker is offline"
      (with-redefs [+r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}]) (pr-str offline-msg)])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        ;; (+r/send-message p (pr-str offline-msg))
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
      
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}])])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        (is (= (+s/get-worker-stats {:queue-name "q1"}) {:task-count 1 :online? true})))
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}]) (pr-str offline-msg)])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        ;; (+r/send-message p (pr-str offline-msg))
        (is (= (+s/get-worker-stats {:queue-name "q2"}) {:task-count 2 :online? false})))
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [(pr-str [{:key "a" :value "b"}]) (pr-str offline-msg) (pr-str away-msg)])]
        ;; (+r/send-message p (pr-str [{:key "a" :value "b"}]))
        ;; (+r/send-message p (pr-str offline-msg))
        ;; (+r/send-message p (pr-str away-msg))
        (is (= (+s/get-worker-stats {:queue-name "q3"}) {:task-count 3 :online? false})))
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [(pr-str offline-msg)])]
        ;; (+r/send-message p (pr-str offline-msg))
        (is (= (+s/get-worker-stats {:queue-name "q4"}) {:task-count 1 :online? false})))
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [(pr-str offline-msg) (pr-str away-msg)])]
        ;; (+r/send-message p (pr-str offline-msg))
        ;; (+r/send-message p (pr-str away-msg))
        (is (= (+s/get-worker-stats {:queue-name "q5"}) {:task-count 2 :online? false}))
        ;; run sedond time to make sure messages can be retrieved again
        (is (= (+s/get-worker-stats {:queue-name "q5"}) {:task-count 2 :online? false})))
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +r/messages-seq (fn [_] [])]
        (is (= (+s/get-worker-stats {:queue-name "q6"}) {:task-count 0 :online? true})))
      ;; no queue
      ;; (is (= (+s/get-worker-stats {:queue-name "non_existent_one"}) {:task-count 0 :online? true}))
      )))
