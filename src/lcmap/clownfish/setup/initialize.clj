(ns lcmap.clownfish.setup.initialize
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :refer [db-cluster]]
            [lcmap.clownfish.event :as event]
            [lcmap.clownfish.setup.cassandra]
            [lcmap.clownfish.setup.rabbitmq]
            [mount.core :as mount]))

(comment
  "In it's initial state, the db does not have a schema and rabbitmq does
 not have queues, bindings or exchanges. These can be created manually:

 user=> (require '[lcmap.clownfish.setup.initialize :as init])
 user=> (init/cassandra environment)
 user=> (init/rabbitmq environment)

 If run from the repl, this will create whatever cassandra schema is provided
 in dev/resources/cassandra.setup.cql, and whatever rabbitmq exchanges, queues and
 bindings are contained in dev/resources/rabbitmq.setup.edn.

 If run from the test profile the same holds true except the file path is
 test/resources/cassandra.setup.cql and test/resources/rabbitmq.setup.edn.

 (start), (stop) and (bounce) may be called from the repl following
 initialization. lcmap.clownfish.system is hard wired to never start
 lcmap.clownfish.setup.cassandra or lcmap.clownfish.setup.rabbitmq states.
 These are only ever started through initialize.clj.

 initialize.clj *can* be used to configure remote systems by manipulating the
 environment parameter.  Additional steps are necessary to create
 anything that doesn't reference the 'local' or 'unit' environments, as both
 cassandra schemas (in dev and test) and the rabbitmq constructs are
 using qualified names that indicate either an environment of 'local' or 'unit'.

 If this is something you really want to do, follow these steps (db as example):
 1 - Modify the dev/resources/cassandra.setup.cql
 2 - Start the repl
 3 - Create an environment dictionary configured for remote server access
 4 - (require '[lcmap.clownfish.setup.initialize :as initialize])
 5 - (initialize/cassandra the-environment)
")

(def system-initialization nil)
(def retry-strategy (again/max-retries 0 (again/constant-strategy 0)))

(defn cassandra
  "Manual operation to set up a db schema."
  [environment]
  (log/infof "initializing cassandra")
  (try
    (alter-var-root #'system-initialization
                    (mount/start #'lcmap.clownfish.configuration/config
                                 #'lcmap.clownfish.db/db-cluster
                                 #'lcmap.clownfish.setup.cassandra/setup
                                 (mount/with-args {:environment environment})))
    (catch Exception e
      (stacktrace/print-stack-trace e)
      (log/errorf "error initializing cassandra: %s"
                  (stacktrace/root-cause e)))
    (finally (mount/stop))))

(defn rabbitmq
  "Manual operation to set up rabbit queues, exchanges and bindings."
  [environment]
  (log/infof "initializing rabbitmq")
  (try
    (alter-var-root #'system-initialization
                    (mount/start #'lcmap.clownfish.configuration/config
                                 #'event/amqp-connection
                                 #'event/amqp-channel
                                 #'lcmap.clownfish.setup.rabbitmq/setup
                                 (mount/with-args {:environment environment})))
    (catch Exception e
      (stacktrace/print-stack-trace e)
      (log/errorf "error initializing event system: %s"
                  (stacktrace/root-cause e)))
    (finally (mount/stop))))
