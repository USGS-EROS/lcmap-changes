(ns lcmap.clownfish.fixtures
  (:require
            [clojure.tools.logging :as log]
            [langohr.exchange :as le]
            [langohr.queue :as lq]
            [lcmap.clownfish.simple :refer [config]]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.event :refer [amqp-channel]]
            [mount.core :as mount :refer [defstate]]))

(defstate exchanges
  :start (let [configs (get-in config [:fixtures :exchanges])]
           (doseq [exchange configs]
             (log/debugf "Creating Exchange: %s" (:name exchange))
             (le/declare amqp-channel
                         (:name exchange)
                         (:type exchange)
                         (:opts exchange)))
           configs))

(defstate queues
  :start (let [configs (get-in config [:fixtures :queues])]
           (doseq [queue configs]
             (log/debugf "Creating Queue: %s" (:name queue))
             (lq/declare amqp-channel (:name queue) (:opts queue)))
           configs))

(defstate bindings
  :start (let [exchanges-state exchanges
               queues-state    queues
               configs         (get-in config [:fixtures :bindings])]
           (doseq [binder configs]
             (log/debugf "Binding %s to %s with opts %s"
                         (:exchange binder)
                         (:queue binder)
                         (:opts binder))
             (lq/bind amqp-channel
                      (:queue binder)
                      (:exchange binder)
                      (:opts binder)))
           configs))

(defstate db-schema
  :start (db/execute-cql "schema.setup.cql" db/db-cluster)
  :stop  (db/execute-cql "schema.teardown.cql" db/db-cluster))
