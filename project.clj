(defproject simba "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :main simba.core
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta2"]
                 [amazonica "0.3.113"]
                 [buddy/buddy-core "1.4.0"]
                 ;; [cheshire "5.8.0"]
                 ;; [clj-http "3.7.0"]
                 [com.climate/squeedo "0.2.1"]
                 [org.clojure/tools.cli "0.3.5"]
                 [prismatic/plumbing "0.5.4"]
                 [io.forward/yaml "1.0.6"]
                 [im.chit/hara.common.error "2.5.10"]
                 [im.chit/hara.expression.form "2.5.10"]
                 [im.chit/hara.function.args "2.5.10"]
                 [pinpointer "0.1.0-SNAPSHOT"]
                 [metosin/spec-tools "0.5.0"]]
  :jvm-opts ^:replace [])
