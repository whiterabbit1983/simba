(ns simba.state-test
  (:require [clojure.test :refer :all]
            [amazonica.aws.sqs :as sqs]
            [simba.state :as +s]))

(deftest get-capacity
  (let [queue-attrs {:ApproximateNumberOfMessages "12"}]
   (testing "Getting queue capacity"
     (with-redefs [sqs/get-queue-attributes (fn [q names] queue-attrs)]
       (is (= (+s/get-capacity "test-queue") 12)))
     (with-redefs [sqs/get-queue-attributes (fn [q names] (dissoc queue-attrs :ApproximateNumberOfMessages))]
       (is (= (+s/get-capacity "test-queue") 0)))
     (with-redefs [sqs/get-queue-attributes (fn [q names] (assoc queue-attrs :ApproximateNumberOfMessages ""))]
       (is (= (+s/get-capacity "test-queue") 0))))))


;; TODO: implement
;; (deftest get-available
;;   (let [workers [{:email "worker@coolcompany.com" :sqs-urn "test-queue-1"}
;;                  {:name "Worker 2" :email "worker2@coolcompany.com" :sqs-urn "test-queue-2"}
;;                  {:name "Worker 2" :email "worker2@coolcompany.com" :sqs-urn "test-queue-2"}]
;;         queue-attrs {:ApproximateNumberOfMessages "12"}]
;;     (testing "Get available workers"
;;       (with-redefs [sqs/get-queue-attributes (fn [q names] queue-attrs)
;;                     sqs/find-queue (fn [q] )]))))
