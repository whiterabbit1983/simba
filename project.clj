(defproject simba "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :main simba.core
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]

                 [amazonica "0.3.118"]
                 [buddy/buddy-core "1.4.0"]
                 [com.cemerick/bandalore "0.0.6" :exclusions [joda-time]]
                 [com.climate/squeedo "0.2.3"]
                 [com.taoensso/timbre "4.10.0"]
                 [im.chit/hara.common.error "2.5.10"]
                 [im.chit/hara.expression.form "2.5.10"]
                 [im.chit/hara.function.args "2.5.10"]
                 [io.forward/yaml "1.0.6"]
                 [metosin/spec-tools "0.6.0-SNAPSHOT"]
                 [org.clojure/tools.cli "0.3.5"]
                 [pinpointer "0.1.0"]]
  :jvm-opts ^:replace [])
