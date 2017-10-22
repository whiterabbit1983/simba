(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [clojure.set :refer [subset?]]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [plumbing.core :as u]
            [yaml.core :as yaml]
            [simba.constants :as c]))

(defn load-worker-def [filepath]
  (->
   (yaml/from-file filepath)
   (u/keywordize-map)))

(defn valid-function? [f]
  (let [flat-f (flatten f)
        f-syms (set (filter symbol? flat-f))
        valid? (subset? f-syms c/allowed-symbols)]

    (and (not (nil? f)) valid?)))

(defn verify-task [task secret]
  (let [nonce (:nonce task)
        nonce-decoded (codecs/hex->bytes nonce)
        raw-task (dissoc task :nonce)
        raw-str (pr-str raw-task)
        mac-params {:key secret :alg :hmac-sha256}]

    (mac/verify raw-str nonce-decoded mac-params)))