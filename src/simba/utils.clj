(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [pinpointer.core :as p]
            [plumbing.core :as plumbing]
            [clojure.spec.alpha :as spec]
            [yaml.core :as yaml]

            [simba.schema :as schema]))

(defn load-worker-def [filepath]
  (->
   (yaml/from-file filepath)
   (plumbing/keywordize-map)))

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
        mac-params {:key secret :alg :hmac-sha256}]

    (mac/verify raw-str nonce-decoded mac-params)))