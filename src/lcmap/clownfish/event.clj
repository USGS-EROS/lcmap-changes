(ns lcmap.clownfish.event
  "Provide RabbitMQ connections, channels, and message handling
  helpers.

  Exchanges, queues, bindings, and  consumers are created in
  namespaces that define function responsible for producing
  and handling messages."
  (:require [camel-snake-kebab.core :refer [->snake_case_keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [dire.core :as dire]
            [langohr.core :as rmq]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.exchange :as le]
            [langohr.queue :as lq]
            [lcmap.clownfish.configuration :refer [config]]
            [mount.core :refer [args defstate stop] :as mount]))

(defn decode-message
  "Convert byte payload to JSON or nil."
  [metadata payload]
  (transform-keys ->snake_case_keyword (json/decode (String. payload "UTF-8"))))

(dire/with-handler! #'decode-message
  java.lang.Exception
  (fn [e & args]
    (log/debugf "cannot decode message: %s"
                {:metadata (first args) :payload (second args) :exception e})
    nil))

(declare amqp-connection amqp-channel)

(defn start-amqp-connection
  "Open RabbitMQ connection."
  [cfg]
  (try
    (log/debugf "starting RabbitMQ connection: %s" cfg)
    (rmq/connect cfg)
    (catch java.lang.RuntimeException ex
      (log/fatal "failed to start RabbitMQ connection: %s" ex))))

(defn stop-amqp-connection
  "Close RabbitMQ connection."
  [conn]
  (try
    (log/debugf "stopping RabbitMQ connection")
    (rmq/close conn)
    (catch java.lang.RuntimeException ex
      (log/error "failed to stop RabbitMQ connection"))
    (finally
      nil)))

(defstate amqp-connection
  :start (start-amqp-connection (:event config))
  :stop  (stop-amqp-connection amqp-connection))

(defn start-amqp-channel
  "Create RabbitMQ channel."
  [connection]
  (try
    (log/debugf "starting RabbitMQ channel")
    (lch/open connection)
    (catch java.lang.RuntimeException ex
      (log/fatal "failed to start RabbitMQ channel"))))

(defn stop-amqp-channel
  "Close RabbitMQ channel."
  [channel]
  (try
    (log/debugf "stopping RabbitMQ channel")
    (lch/close channel)
    (catch com.rabbitmq.client.AlreadyClosedException e
      (log/warnf "failed to stop RabbitMQ channel"))
    (finally
      nil)))

(defstate amqp-channel
  :start (start-amqp-channel amqp-connection)
  :stop  (stop-amqp-channel amqp-channel))

(defn configure-channel
  [channel]
  (do
    (lb/qos channel 1)))

(defstate configure-amqp-channel
  :start (configure-channel amqp-channel))
