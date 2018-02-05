(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as spec]
            [clojure.walk :refer [keywordize-keys]]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [pinpointer.core :as p]
            [yaml.core :as yaml]
            [simba.schema :as schema])

  (:import  [com.amazonaws.regions Region Regions]))


(defn load-worker-def [filepath]
  (->
   (yaml/from-file filepath)
   (keywordize-keys)))


(defn valid-worker-def? [workers]
  (let [valid? (spec/valid? schema/workers-schema workers)]

    (if-not valid?
      (println (p/pinpoint schema/workers-schema workers))
      valid?)))


(defn valid-task? [task]
  (let [valid? (spec/valid? schema/task-schema task)]

    (if-not valid?
      (println (p/pinpoint schema/task-schema task))
      valid?)))


(defn verify-task [task secret]
  (let [nonce (:nonce task)
        nonce-decoded (codecs/hex->bytes nonce)
        raw-task (dissoc task :nonce)
        raw-str (pr-str raw-task)
        mac-params {:key secret :alg :hmac+sha256}]

    (mac/verify raw-str nonce-decoded mac-params)))


(defn get-region [region-str]

  (->> (-> (str/upper-case region-str)
           (str/replace "-" "_"))

       Regions/valueOf
       Region/getRegion))


(defn before-shutdown [f & args]

  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (apply f args)))))
