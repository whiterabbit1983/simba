(ns simba.utils
  (:refer-clojure :exclude [load])
  (:require [plumbing.core :as u]
            [yaml.core :as yaml]))

(defn load-worker-def [filepath]
  (->
   (yaml/from-file filepath)
   (u/keywordize-map)))