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
                 [pinpointer "0.1.0"]
                 [clj-time "0.14.3"]
                 [org.apache.activemq/activemq-client "5.15.0"]
                 [org.apache.activemq/activemq-pool "5.15.0"]
                 [com.rabbitmq/amqp-client "5.2.0"]
                 [javax.jms/jms-api "1.1-rev-1"]]
  :profiles {:dev {:dependencies [[org.apache.activemq/activemq-broker "5.15.0"]
                                  [org.apache.activemq/activemq-spring "5.15.0"]
                                  [org.apache.activemq/activemq-kahadb-store "5.15.0"]]}}
  :jvm-opts ^:replace [])
