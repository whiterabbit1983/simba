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
  [endpoint & {:keys [user password max-connections use-pool] :or {max-connections 10 use-pool true}}]
  (let [amq-factory (amq-conn-factory
                      endpoint
                      :user user
                      :password password)]
    (doto
      (.createConnection
       (if use-pool
         (pooled-conn-factory amq-factory :max-connections max-connections)
         amq-factory))
      (.start))))


(defprotocol Closeable
  (close [this]))


(defprotocol ProducerProtocol
  (send-message [this msg]))


(defprotocol ConsumerProtocol
  (receive [this timeout])
  (messages-seq [this timeout]))


(deftype Consumer [consumer]
  ConsumerProtocol
  (receive [this timeout]
    (.getText (.receive (.consumer this) timeout)))
  (messages-seq [this timeout]
    (letfn [(recv []
              (try (receive this timeout)
                   (catch NullPointerException e nil)))]
      (take-while #(not (nil? %)) (repeatedly #(recv)))))
  Closeable
  (close [this]
    (.close (.consumer this))))


(deftype Producer [producer]
  ProducerProtocol
  (send-message [this msg]
    (if (nil? *session*)
      (error "ActiveMQ session not initialized")
      (do
        (.send (.producer this) (.createTextMessage *session* msg))
        this)))
  Closeable
  (close [this]
    (.close (.producer this))))


(defn get-producer
  "Create message producer with the given queue name as a destination"
  [queue & {:keys [delivery-mode] :or {delivery-mode DeliveryMode/NON_PERSISTENT}}]
  (if (nil? *session*)
    (error "ActiveMQ session not initialized")
    (let [dest (.createQueue *session* queue)]
      (->Producer (doto
                      (.createProducer *session* dest)
                      (.setDeliveryMode delivery-mode))))))


(defn get-consumer
  "Create message consumer for a given queue"
  [queue]
  (if (nil? *session*)
    (error "ActiveMQ session not initialized")
    (->Consumer (.createConsumer *session* (.createQueue *session* queue)))))


(defn create-session
  "Create new session"
  [& {:keys [ack-mode] :or {ack-mode Session/CLIENT_ACKNOWLEDGE}}]
  (if (nil? *connection*)
    (error "ActiveMQ connection not initialized")
    (.createSession *connection* false ack-mode)))


(defn init-connection
  "Initialize connection to the broker"
  [endpoint & {:keys [user password max-connections use-pool] :or {max-connections 10 use-pool true}}]
  (let [conn (create-connection
              endpoint
              :use-pool use-pool
              :user user
              :password password
              :max-connections max-connections)]
    (alter-var-root (var *connection*) (fn [v] conn))
    (alter-var-root (var *session*) (fn [v] (create-session)))))


(defn close-connection
  "Close connection"
  []
  (.close *session*)
  (.close *connection*))
