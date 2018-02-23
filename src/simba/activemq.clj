(ns simba.activemq
  (:require [hara.common.error :refer [error]])
  (:import [org.apache.activemq ActiveMQConnectionFactory]
           [org.apache.activemq.jms.pool PooledConnectionFactory]
           [javax.jms Session DeliveryMode]))


(def ^:dynamic *connection* nil)
(def ^:dynamic *session* nil)


(defn- amq-conn-factory
  [endpoint & {:keys [user password]}]
  (let [factory (ActiveMQConnectionFactory.)]
    (.setBrokerURL factory endpoint)
    (if-not (nil? user)
      (.setUserName factory user))
    (if-not (nil? password)
      (.setPassword factory password))
    factory))


(defn- pooled-conn-factory
  [conn-factory & {:keys [max-connections] :or {max-connections 10}}]
  (doto
      (PooledConnectionFactory.)
      (.setConnectionFactory conn-factory)
      (.setMaxConnections max-connections)))


(defn- create-connection
  [endpoint & {:keys [user password max-connections] :or {max-connections 10}}]
  (let [conn-factory (amq-conn-factory
                      endpoint
                      :user user
                      :password password)
        pool (pooled-conn-factory conn-factory :max-connections max-connections)]
    (doto
      (.createConnection pool)
      (.start))))


(defn send-message
  "Send message to the queue"
  [producer msg]
  (let [prod-msg (.createTextMessage *session* msg)]
    (.send producer prod-msg)
    (.close producer)))


(defn get-producer
  "Create message producer with the given queue name as a destination"
  [queue]
  (if (nil? *session*)
    (error "ActiveMQ connection not initialised")
    (let [dest (.createQueue *session* queue)]
      (doto
          (.createProducer *session* dest)
          (.setDeliveryMode DeliveryMode/NON_PERSISTENT)))))


(defn init-connection
  "Initialize connection to the broker"
  [endpoint & {:keys [user password max-connections] :or {max-connections 10}}]
  (let [conn (create-connection
              endpoint
              :user user
              :password password
              :max-connections max-connections)]
    (alter-var-root (var *connection*) (fn [v] conn))
    (alter-var-root (var *session*) (fn [v] (.createSession conn false Session/AUTO_ACKNOWLEDGE)))))


(defn close-connection
  "Close connection"
  []
  (.close *session*)
  (.close *connection*))
