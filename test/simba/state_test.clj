(ns simba.state-test
  (:require [clojure.test :refer :all]
            [amazonica.aws.sqs :as sqs]
            [simba.state :as +s]))


(deftest get-capacity-tests
  (let [queue-attrs {:ApproximateNumberOfMessages "12"}]
   (testing "Getting queue capacity"
     (with-redefs [sqs/get-queue-attributes (fn [q names] queue-attrs)]
       (is (= (+s/get-capacity "test-queue") 12)))
     (with-redefs [sqs/get-queue-attributes (fn [q names] (dissoc queue-attrs :ApproximateNumberOfMessages))]
       (is (= (+s/get-capacity "test-queue") 0)))
     (with-redefs [sqs/get-queue-attributes (fn [q names] (assoc queue-attrs :ApproximateNumberOfMessages ""))]
       (is (= (+s/get-capacity "test-queue") 0))))))


(deftest get-available-tests
  (with-redefs [sqs/find-queue (fn [q] (if (nil? q) (throw (NullPointerException.)) q))]
    (is (thrown-with-msg? Exception #"Worker queues not found" (+s/get-available [{:email "w@cc.com"}]))))
  (with-redefs [sqs/find-queue (fn [q] (str q))]
    (testing "Empty list of workers"
      (is (thrown-with-msg? Exception #"Worker queues not found" (+s/get-available []))))
    (testing "One worker provided"
      (with-redefs [+s/get-capacity (fn [q] 0)]
        (is (empty? (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1"}])))
        (is (empty? (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 0}])))
        (is (= (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1}])
               [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1 :current 0 :above-hwm? false}]))
        (is (= (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1 :hwm 0}])
               [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1 :current 0 :hwm 0 :above-hwm? false}]))))
    (testing "Multiple workers provided"
      (with-redefs [+s/get-capacity (fn [q] 1)]
        (is (empty? (+s/get-available [{:email "w1@cc.com"} {:email "w2@cc.com"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :sqs-urn "test-receiver-1"}
                                    {:email "w2@cc.com" :sqs-urn "test-receiver-2"}])))
        (is (empty? (+s/get-available [{:email "w1@cc.com" :sqs-urn "test-receiver-1" :capacity 0}
                                    {:email "w2@cc.com" :sqs-urn "test-receiver-2" :capacity 0}])))
        (is (= (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1}
                               {:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 2}])
               [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 2 :current 1 :above-hwm? true}]))
        (is (= (+s/get-available [{:email "w1@cc.com" :sqs-urn "test-receiver-1" :capacity 1}
                               {:email "w2@cc.com" :sqs-urn "test-receiver-1" :capacity 2 :hwm 1}])
               [{:email "w2@cc.com" :sqs-urn "test-receiver-1" :capacity 2 :hwm 1 :current 1 :above-hwm? false}])))))
  (testing "sqs/find-queue returns nil"
    (with-redefs [sqs/find-queue (fn [q] nil)]
      (is (thrown-with-msg? Exception #"Worker queues not found"
                            (+s/get-available [{:email "w@cc.com" :sqs-urn "test-receiver-1" :capacity 1}
                                               {:email "w2@cc.com" :sqs-urn "test-receiver-2" :capacity 1}]))))))
