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
      (with-redefs [+r/message-count (fn [_] 0)]
        (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1"}])))
        (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 0}])))
        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1}])
               [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :above-hwm? false}]))
        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :hwm 0}])
               [{:email "w@cc.com" :queue-name "test-receiver-1" :capacity 1 :current 0 :hwm 0 :above-hwm? false}]))))
    (testing "Multiple workers provided"
      (with-redefs [+r/message-count (fn [_] 0)]
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1"}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 0}
                                       {:email "w2@cc.com" :queue-name "test-receiver-2" :capacity 0}]))))
      (with-redefs [+s/get-worker-stats (fn [_] {:task-count 1 :online? true})]

        (is (= (+s/get-available [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 1}
                                  {:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2}])
               [{:email "w@cc.com" :queue-name "test-receiver-3" :capacity 2 :current 1 :above-hwm? true}])))
      (with-redefs [+s/get-worker-stats (fn [_] {:task-count 1 :online? true})]
        (is (= (+s/get-available [{:email "w1@cc.com" :queue-name "test-receiver-1" :capacity 1}
                                  {:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1}])
               [{:email "w2@cc.com" :queue-name "test-receiver-1" :capacity 2 :hwm 1 :current 1 :above-hwm? false}]))))
    (testing "worker is offline"
      (with-redefs [+s/get-worker-stats (fn [_] {:task-count 1 :online? false})]
        (is (empty? (+s/get-available [{:email "w@cc.com" :queue-name "queue-1" :capacity 1}])))))))

(deftest get-workers-stats-tests
    (testing "checking queue name"
      (is (thrown? AssertionError (+s/get-worker-stats {})))
      (is (thrown? AssertionError (+s/get-worker-stats {:queue-name 5})))
      (is (thrown? AssertionError (+s/get-worker-stats {:queue-name ""})))))
