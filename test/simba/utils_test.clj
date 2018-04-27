(ns simba.utils-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [yaml.core :as y]
            [simba.utils :as +u]))


(defn ->file [s]
  (let [tmp (java.io.File/createTempFile "test" ".yaml")
        tmp-path (.getAbsolutePath tmp)]
    (spit tmp-path s)
    tmp-path))


(defn gen-yaml [m]
  (->
   (y/generate-string m :dumper-options {:flow-style :block})
   (->file)))


(deftest yaml->map
  (testing "YAML to map conversion"
    (is (= {:a 1 :b {:c {:d 2}} :e "d"} (-> (gen-yaml {:a 1 :b {:c {:d 2}} "e" "d"}) +u/yaml->map)))
    (let [tmp-path (->file "\"a\": 1")]
      (is (= (+u/yaml->map tmp-path) {:a 1})))))


(deftest verify-task
  (let [nonce-1 "e9bde63075977f8447b1cfe69fdcb0d88cb83d0f647e7f7bda7f1006ae64871c"
        nonce-2 "840eca7279d6184e64a2d1c7e0c40033ca14e3d70bc6ae7789c91a46d45136b4"
        nonce-3 ""
        task-1 {:id "task 1" :nonce nonce-1 :created-at 123 :retries 3 :timeout 300 :payload [1 2 3] :assigner 0}]
    (testing "Verify task signature"
      (is (+u/verify-task task-1 "secret"))
      (is (not (+u/verify-task (assoc task-1 :id "task 2") "secret")))
      (is (not (+u/verify-task task-1 "secret1")))
      (is (not (+u/verify-task (assoc task-1 :nonce nonce-2) "secret")))
      (is (not (+u/verify-task (assoc task-1 :nonce nonce-3) "secret"))))
   (testing "Check parameters"
     (is (thrown-with-msg? Exception #"Task .* does not contain :nonce key"
                           (+u/verify-task (dissoc task-1 :nonce) "secret" :silent false)))
     (is (not (+u/verify-task (dissoc task-1 :nonce) "secret" :silent true))))))


(deftest task->map
  (testing "Task payload to map conversion"
    (is (= (+u/task->map [{:key "a" :value "b"} {:key :c :value 1}]) {:a "b" :c 1}))
    (is (= (+u/task->map []) {}))
    (is (= (+u/task->map [{}]) {}))
    (is (= (+u/task->map [{:key "a" :some-value "b"}]) {:a nil}))
    (is (= (+u/task->map [{:some-key "a" :some-value "b"}]) {}))))
