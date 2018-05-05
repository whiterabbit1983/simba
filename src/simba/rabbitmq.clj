(ns simba.rabbitmq
  (:require [hara.common.error :refer [error]])
  (:import [javax.jms Session DeliveryMode]
           [com.rabbitmq.client Connection ConnectionFactory MessageProperties]))


(def persistent MessageProperties/PERSISTENT_TEXT_PLAIN)
(def ^:dynamic *connection* nil)
;; (def ^:dynamic *session* nil)


(defn init-connection
  "Create RabbitMQ connection"
  [uri]
  (let [factory (ConnectionFactory.)]
    (.setUri factory uri)
    (.setAutomaticRecoveryEnabled factory true)
    (alter-var-root (var *connection*) (fn [v] (.newConnection factory)))))


;; (defn- amq-conn-factory
;;   [endpoint & {:keys [user password]}]
;;   (let [factory (ActiveMQConnectionFactory.)]
;;     (.setBrokerURL factory endpoint)
;;     (if-not (nil? user)
;;       (.setUserName factory user))
;;     (if-not (nil? password)
;;       (.setPassword factory password))
;;     factory))


;; (defn- pooled-conn-factory
;;   [conn-factory & {:keys [max-connections] :or {max-connections 10}}]
;;   (doto
;;       (PooledConnectionFactory.)
;;       (.setConnectionFactory conn-factory)
;;       (.setMaxConnections max-connections)))


;; (defn- create-connection
;;   [endpoint & {:keys [user password max-connections use-pool] :or {max-connections 10 use-pool true}}]
;;   (let [amq-factory (amq-conn-factory
;;                       endpoint
;;                       :user user
;;                       :password password)]
;;     (doto
;;       (.createConnection
;;        (if use-pool
;;          (pooled-conn-factory amq-factory :max-connections max-connections)
;;          amq-factory))
;;       (.start))))


(defprotocol Closeable
  (close [this]))


;; (defprotocol ProducerProtocol
;;   (send-message [this msg]))


;; (defprotocol ConsumerProtocol
;;   (receive [this])
;;   (messages-seq [this]))

(defprotocol ChannelProtocol
  (receive [this])
  (messages-seq [this])
  (send-message [this msg]))

(deftype Channel [channel queue]
  ChannelProtocol
  (receive [this]
    (-> (.channel this)
        (.basicGet (.queue this) false)
        (.getBody)
        (String.)))
  (messages-seq [this]
    (letfn [(recv []
              (try (receive this)
                   (catch NullPointerException e nil)))]
      (take-while #(not (nil? %)) (repeatedly #(recv)))))
  (send-message [this msg]
    (if (nil? *connection*)
      (error "RabbitMQ connection not initialized")
      (do
        (.confirmSelect (.channel this))
        (.basicPublish (.channel this) "" (.queue this) persistent (.getBytes msg))
        this)))
  Closeable
  (close [this]
    (.close (.channel this))))


;; (deftype Producer [channel queue]
;;   ProducerProtocol
;;   (send-message [this msg]
;;     (if (nil? *connection*)
;;       (error "RabbitMQ connection not initialized")
;;       (do
;;         (.basicPublish (.channel this) "" (.queue this) nil (.getBytes msg))
;;         this)))
;;   Closeable
;;   (close [this]
;;     (.close (.channel this))))


(defn get-channel
  "Create message producer with the given queue name as a destination"
  [queue]
  (if (nil? *connection*)
    (error "RabbitMQ connection not initialized")
    (let [channel (.createChannel *connection*)]
      (.queueDeclare channel queue true false false nil)
      (->Channel channel queue))))


;; (defn get-consumer
;;   "Create message consumer for a given queue"
;;   [queue]
;;   (if (nil? *connection*)
;;     (error "RabbitMQ connection not initialized")
;;     (let [channel (.createChannel *connection*)]
;;       (.queueDeclare channel queue true false false nil)
;;       (->Consumer channel queue))))


;; (defn create-session
;;   "Create new session"
;;   [& {:keys [ack-mode] :or {ack-mode Session/CLIENT_ACKNOWLEDGE}}]
;;   (if (nil? *connection*)
;;     (error "ActiveMQ connection not initialized")
;;     (.createSession *connection* false ack-mode)))


;; (defn init-connection
;;   "Initialize connection to the broker"
;;   [endpoint & {:keys [user password max-connections use-pool] :or {max-connections 10 use-pool true}}]
;;   (let [conn (create-connection
;;               endpoint
;;               :use-pool use-pool
;;               :user user
;;               :password password
;;               :max-connections max-connections)]
;;     (alter-var-root (var *connection*) (fn [v] conn))
;;     (alter-var-root (var *session*) (fn [v] (create-session)))))


(defn close-connection
  "Close connection"
  []
  ;; (.close *session*)
  (.close *connection*))
