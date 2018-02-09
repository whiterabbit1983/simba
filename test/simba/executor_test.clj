(ns simba.executor-test
  (:require [clojure.test :refer :all]
            [amazonica.aws.sqs :as sqs]
            [simba.executor :as +e]
            [simba.state :as +s]))


(deftest exec-tests
   (testing "Test for errors"
     (is (thrown-with-msg? Exception #"Function accepts incorrect number of args"
                           (+e/exec '(+ 1 %) 3 4 5)))
     (is (nil? (+e/exec '(throw (Exception. %)) "str"))))
   (testing "Test function execution"
     (is (= 2 (+e/exec '(+ 1 %) 1)))))


;; (deftest process-task-tests
;;   (testing "Wrong parameters"
;;     (is (thrown? AssertionError (+e/process-task {} {}))))
;;   (testing "Various task options"
;;     (with-derefs [+s/dispatch (fn [q t] q)]
;;       (is (= {} (+s/process-task {}
;;                                  {:workers [{}] :secret "secret" :input-queue "test-input-queue"}))))))
