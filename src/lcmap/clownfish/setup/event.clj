(ns lcmap.clownfish.setup.event
  (:require
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [langohr.exchange :as le]
            [langohr.queue :as lq]))

(defn setup-exchanges
  [amqp-channel exchange-defs]
  (doseq [exchange exchange-defs]
    (log/debugf "Creating Exchange: %s" (:name exchange))
    (le/declare amqp-channel
                (:name exchange)
                (:type exchange)
                (:opts exchange)))
  exchange-defs)

(defn setup-queues
  [amqp-channel queue-defs]
  (doseq [queue queue-defs]
    (log/debugf "Creating Queue: %s" (:name queue))
    (lq/declare amqp-channel (:name queue) (:opts queue)))
  queue-defs)

(defn setup-bindings
  [amqp-channel binding-defs]
  (doseq [binder binding-defs]
    (log/debugf "Binding %s to %s with opts %s"
      (:exchange binder)
      (:queue binder)
      (:opts binder))
    (lq/bind amqp-channel
      (:queue binder)
      (:exchange binder)
      (:opts binder)))
  binding-defs)

(defn setup
  "Sets up all exchanges, queues and bindings on the amqp channel"
  [{:keys [exchanges queues bindings amqp-channel] :as data}]
  (setup-exchanges amqp-channel exchanges)
  (setup-queues amqp-channel queues)
  (setup-bindings amqp-channel bindings)
  :done)
