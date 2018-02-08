(ns simba.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [pinpointer.core :as p]
            [simba.utils :as +u]
            [simba.schema :as +sc]))

;; (deftest test-workers-def
;;   (testing "Test correct example worker definition"
;;     (is (s/valid? +sc/workers-schema
;;                   (+u/load-worker-def "./test_worker_def.yml")))))

;; (deftest test-incorrect-def
;;   (testing "Test incorrect example worker definition"
;;     (is (not
;;          (s/valid? +sc/workers-schema
;;                    (+u/load-worker-def "./incorrect_worker_def.yml"))))))
