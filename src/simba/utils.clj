(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [clojure.set :refer [subset?]]
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