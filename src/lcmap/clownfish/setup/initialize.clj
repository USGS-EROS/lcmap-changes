(ns lcmap.clownfish.setup.initialize
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :refer [db-cluster]]
            [lcmap.clownfish.event :as event]
            [lcmap.clownfish.setup.db]
            [lcmap.clownfish.setup.event]
            [mount.core :as mount]))

(def system-initialization nil)
(def retry-strategy (again/max-retries 0 (again/constant-strategy 0)))

(defn db
  "Manual operation to set up a db schema."
  [environment]
  (try
    (alter-var-root #'system-initialization
                    (mount/start #'lcmap.clownfish.configuration/config
                                 #'lcmap.clownfish.db/db-cluster
                                 #'lcmap.clownfish.setup.db/setup
                                 (mount/with-args {:environment environment})
                      (mount/stop  #'lcmap.clownfish.configuration/config
                                   #'lcmap.clownfish.db/db-cluster
                                   #'lcmap.clownfish.setup.db/setup)))
    (catch Exception e
      (stacktrace/print-stack-trace e)
      (log/errorf "error initializing db: %s" (stacktrace/root-cause e)))))

(defn eventing
  "Manual operation to set up rabbit queues, exchanges and bindings."
  [environment]
  (try
    (alter-var-root #'system-initialization
                    (mount/start #'lcmap.clownfish.configuration/config
                                 #'event/amqp-connection
                                 #'event/amqp-channel
                                 #'lcmap.clownfish.setup.event/setup
                                 (mount/with-args {:environment environment})
                      (mount/stop #'lcmap.clownfish.configuration/config
                                  #'lcmap.clownfish.event/amqp-connection
                                  #'lcmap.clownfish.event/amqp-channel
                                  #'lcmap.clownfish.setup.event/setup)))
    (catch Exception e
      (stacktrace/print-stack-trace e)
      (log/errorf "error initializing event system: %s"
                  (stacktrace/root-cause e)))))
