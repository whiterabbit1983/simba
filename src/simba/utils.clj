(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as spec]
            [clojure.walk :refer [keywordize-keys]]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [taoensso.timbre :as log]
            [pinpointer.core :as p]
            [hara.common.error :refer [error]]
            [yaml.core :as yaml]
            [simba.schema :as schema])

  (:import  [com.amazonaws.regions Region Regions]))


(defn yaml->map [filepath]
  "Load YAML file and convert it to a map"
  (->
   (yaml/from-file filepath)
   (keywordize-keys)))


(defn task->map
  "Converts a payload of the form [{:key \"a\" :value \"b\"}] to a map of the form {:a \"b\"}"
  [task]
  (reduce (fn [acc val]
            (let [key (:key val)]
              (if-not (nil? key)
                (assoc acc
                  (if (string? key) (keyword key) key)
                  (:value val))
                acc)))
          {} task))


(defn verify-task [task secret & {:keys [silent] :or {silent true}}]
  (let [nonce (:nonce task)
        raw-task (dissoc task :nonce)
        raw-str (pr-str raw-task)
        mac-params {:key secret :alg :hmac+sha256}
        err-msg (str "Task " task " does not contain :nonce key")]
    (if (not (nil? nonce))
      (mac/verify raw-str (codecs/hex->bytes nonce) mac-params)
      (do
        (if silent
          (do
            (log/error err-msg)
            false)
          (error err-msg))))))


(defn get-region [region-str]

  (->> (-> (str/upper-case region-str)
           (str/replace "-" "_"))

       Regions/valueOf
       Region/getRegion))


(defn before-shutdown [f & args]

  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (apply f args)))))
