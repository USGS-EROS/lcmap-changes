(ns lcmap.clownfish.setup.rabbitmq
  (:require [clojure.edn :as edn]
            [clojure.stacktrace :as stacktrace]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [langohr.exchange :as le]
            [langohr.queue :as lq]
            [lcmap.clownfish.event :refer [amqp-channel]]
            [mount.core :as mount :refer [defstate]]))

(defn setup-exchanges
  [channel exchange-defs]
  (doseq [exchange exchange-defs]
    (log/debugf "Creating Exchange: %s" (:name exchange))
    (le/declare channel
                (:name exchange)
                (:type exchange)
                (:opts exchange)))
  exchange-defs)

(defn setup-queues
  [channel queue-defs]
  (doseq [queue queue-defs]
    (log/debugf "Creating Queue: %s" (:name queue))
    (lq/declare channel (:name queue) (:opts queue)))
  queue-defs)

(defn setup-bindings
  [channel binding-defs]
  (doseq [binder binding-defs]
    (log/debugf "Binding %s to %s with opts %s"
      (:exchange binder)
      (:queue binder)
      (:opts binder))
    (lq/bind channel
      (:queue binder)
      (:exchange binder)
      (:opts binder)))
  binding-defs)

(def event-setup (try
                  (edn/read-string (slurp (io/resource "rabbitmq.setup.edn")))
                  (catch Exception e
                   (log/warnf "Could not load rabbitmq.setup.edn: %s"
                              (stacktrace/root-cause e)
                    nil))))

(defstate setup
  "Sets up all exchanges, queues and bindings on the amqp channel"
  :start (do (log/debugf "Setting up event system: %s" (:exchanges event-setup))
             (setup-exchanges amqp-channel (:exchanges event-setup))
             (setup-queues amqp-channel (:queues event-setup))
             (setup-bindings amqp-channel (:bindings event-setup))
           event-setup))
