(ns simba.rabbitmq
  (:require [hara.common.error :refer [error]])
  (:import [javax.jms Session DeliveryMode]
           [com.rabbitmq.client Connection ConnectionFactory MessageProperties]))


(def persistent MessageProperties/PERSISTENT_TEXT_PLAIN)
(def ^:dynamic *connection* nil)


(defn init-connection
  "Create RabbitMQ connection"
  [uri]
  (let [factory (ConnectionFactory.)]
    (.setUri factory uri)
    (.setAutomaticRecoveryEnabled factory true)
    (alter-var-root (var *connection*) (fn [v] (.newConnection factory)))))


(defprotocol Closeable
  (close [this]))


(defprotocol ChannelProtocol
  (receive [this])
  (messages-seq [this])
  (send-message [this msg])
  (message-count [this]))


(deftype Channel [channel queue]
  ChannelProtocol
  (receive [this]
    (-> (.channel this)
        (.basicGet (.getQueue (.queue this)) false)
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
        (.basicPublish (.channel this) "" (.getQueue (.queue this)) persistent (.getBytes msg))
        this)))
  (message-count [this]
    (.getMessageCount (.queue this)))
  Closeable
  (close [this]
    (.close (.channel this))))


(defn get-channel
  "Create message producer with the given queue name as a destination"
  [queue]
  (if (nil? *connection*)
    (error "RabbitMQ connection not initialized")
    (let [channel (.createChannel *connection*)
          queue (.queueDeclare channel queue true false false nil)]
      (->Channel channel queue))))


(defn close-connection
  "Close connection"
  []
  (.close *connection*))
