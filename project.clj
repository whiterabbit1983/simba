(defproject simba "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :main simba.core
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta2"]

                 [amazonica "0.3.113"]
                 [buddy/buddy-core "1.4.0"]
                 [com.cemerick/bandalore "0.0.6"]
                 [com.climate/squeedo "0.2.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [im.chit/hara.common.error "2.5.10"]
                 [im.chit/hara.expression.form "2.5.10"]
                 [im.chit/hara.function.args "2.5.10"]
                 [io.forward/yaml "1.0.6"]
                 [metosin/spec-tools "0.5.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [pinpointer "0.1.0-SNAPSHOT"]
                 [prismatic/plumbing "0.5.4"]]
  :jvm-opts ^:replace [])
