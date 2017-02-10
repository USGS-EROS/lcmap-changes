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

(comment
 "In it's initial state, the db does not have a schema and rabbitmq does
 not have queues, bindings or exchanges. These can be created manually:

 user=> (require '[lcmap.clownfish.setup.initialize :as init])
 user=> (init/db environment)
 user=> (init/eventing environment)

 If run from the repl, this will create whatever cassandra schema is provided
 in dev/resources/schema.setup.cql, and whatever rabbitmq exchanges, queues and
 bindings are contained in dev/resources/rabbit-setup.edn.

 If run from the test profile the same holds true except the file path is
 test/resources/schema.setup.cql and test/resources/rabbit-setup.edn.

 (start), (stop) and (bounce) may be called from the repl following
 initialization. lcmap.clownfish.system is hard wired to never start
 lcmap.clownfish.setup.db or lcmap.clownfish.setup.event states.
 These are only ever started through initialize.clj.

 initialize.clj *can* be used to configure remote systems by manipulating the
 environment parameter.  Additional steps are necessary to create
 anything that doesn't reference the 'local' or 'unit' environments, as both
 cassandra schemas (in dev and test) and the rabbitmq constructs are
 using qualified names that indicate either an environment of 'local' or 'unit'.

 If this is something you really want to do, follow these steps (db as example):
 1 - Modify the dev/resources/scheme.setup.cql
 2 - Start the repl
 3 - Create an environment dictionary configured for remote server access
 4 - (require '[lcmap.clownfish.setup.initialize :as initialize])
 5 - (initialize/db the-environment)
")

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
