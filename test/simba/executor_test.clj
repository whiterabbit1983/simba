(ns simba.executor-test
  (:require [clojure.test :refer :all]
            [simba.executor :as +e]
            [simba.state :as +s]
            [simba.settings :as settings]
            [simba.rabbitmq :as +r]))


(deftest exec-tests
   (testing "Test for errors"
     (is (thrown-with-msg? Exception #"Function accepts incorrect number of args"
                           (+e/exec '(+ 1 %) 3 4 5)))
     (is (nil? (+e/exec '(throw (Exception. %)) "str"))))
   (testing "Test function execution"
     (is (= 2 (+e/exec '(+ 1 %) 1)))))


(def ^:dynamic *process-task-output* {})
(deftype ChannelMock [] +r/Closeable (close [this] nil))
(def ch-mock (->ChannelMock))


(deftest process-task-tests
  (testing "Wrong parameters"
    (is (thrown? AssertionError (+e/process-task {} {})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0} {})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload []} {})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload [] :retries 1} {})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload [] :retries 1 :timeout 1} {})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload [] :retries 1 :timeout 1}
                                                 {:workers []})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload [] :retries 1 :timeout 1}
                                                 {:workers [] :secret "secret"})))
    (is (thrown? AssertionError (+e/process-task {:assigner 0 :payload [] :retries 1 :timeout 1}
                                                 {:workers [] :input-queue "q"}))))
  (testing "Task not verified"
    (with-redefs [+r/get-channel (fn [_] ch-mock)
                  +r/messages-seq (fn [_] [])
                  +s/get-worker-stats (fn [_] {:task-count 0 :online? true})]
      (let [worker {:queue-name "recv-q" :capacity 0}]
        (is (thrown-with-msg? Exception #"Task could not be verified"
                              (+e/process-task {:id "task1" :nonce "" :created-at 123 :retries 1 :timeout 1 :payload [] :assigner 0}
                                               {:input-queue "q" :secret "secret" :workers [worker]}))))))
  (testing "Retries exhausted"
    (binding [*process-task-output* {}]
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +s/get-worker-stats (fn [_] {:task-count 0 :online? true})
                    +r/send-message (fn [m] m)
                    +s/dispatch (fn [q t] (set! *process-task-output* (assoc *process-task-output* :q q :t t)))]
        (let [task {:id "task1"
                    :nonce "554252c0dd3a9b094d3ed69c2690372806088e914ee73c488e87124c50d2fd24"
                    :created-at 123
                    :retries -1
                    :timeout 900
                    :payload [{:key "a" :value "b"}]
                    :assigner 0}
              worker {:queue-name "recv-q" :capacity 0}
              opts {:input-queue "q" :workers [worker] :secret "secret"}]
          (is (and
               (= task (+e/process-task task opts))
               (= *process-task-output* {:q (str settings/task-queue-prefix "q-failed") :t task})))))))
  (testing "No workers available"
    (with-redefs [+r/get-channel (fn [_] ch-mock)
                  +s/get-worker-stats (fn [_] {:task-count 0 :online? true})
                  +r/send-message (fn [m] m)]
      (let [task {:id "task1"
                  :nonce "b68b72d641f025d0474dccbccf98a0f6bd0c25364ffb9e07f2e335d50b09c7fc"
                  :created-at 123
                  :retries 1
                  :timeout 900
                  :payload [{:key "a" :value "b"}]
                  :assigner 0}
            worker {:queue-name "recv-q" :capacity 0}
            opts {:input-queue "q" :workers [worker (assoc worker :queue-name "recv-q-2")] :secret "secret"}]
        (is (= (assoc task :nack 900 :retries 0) (+e/process-task task opts))))))
  (testing "Task dispatched successfully"
    (binding [*process-task-output* {}]
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +s/get-worker-stats (fn [_] {:task-count 0 :online? true})
                    +r/send-message (fn [m] m)
                    +s/dispatch (fn [q t] (set! *process-task-output* (assoc *process-task-output* :q q :t t)))]
        (let [task {:id "task1"
                    :nonce "b68b72d641f025d0474dccbccf98a0f6bd0c25364ffb9e07f2e335d50b09c7fc"
                    :created-at 123
                    :retries 1
                    :timeout 900
                    :payload [{:key "a" :value "b"}]
                    :assigner 0}
              worker {:email "w@cc.com" :queue-name "recv-q" :capacity 1}
              opts {:input-queue "q" :workers [worker] :secret "secret"}]
          (is (= (assoc task :status "completed") (+e/process-task task opts)))
          (is (= *process-task-output* {:q (str settings/task-queue-prefix "recv-q") :t [{:key "a" :value "b"}]}))))))
  (testing "Processing failed"
    (binding [*process-task-output* {}]
      (with-redefs [+r/get-channel (fn [_] ch-mock)
                    +s/get-worker-stats (fn [_] {:task-count 0 :online? true})
                    +r/send-message (fn [m] m)
                    +s/dispatch (fn [q t] (set! *process-task-output* (assoc *process-task-output* :q q :t t)))]
        (let [task {:id "task1"
                    :nonce "52bacd6dd4456b9bd19205d2d3f21a5569e8c2fe9b2431dd70518e5ff87e8e8e"
                    :created-at 123
                    :retries 1
                    :timeout 900
                    :payload [{:key "a" :value "b"}]
                    :assigner 1}
              worker {:email "w@cc.com" :queue-name "recv-q" :capacity 1}
              opts {:input-queue "q" :workers [worker] :secret "secret"}]
          (is (= (+e/process-task task opts) task)))))))
