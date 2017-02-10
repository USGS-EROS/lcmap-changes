(ns user
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [lcmap.clownfish.system :as system]

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


(def system-var nil)
(def retry-strategy (again/max-retries 0 (again/constant-strategy 0)))
(def environment (edn/read-string (slurp (io/resource "environment.edn"))))

(defn start
  []
  (alter-var-root #'system-var
    (try
      (system/start environment retry-strategy)
      (catch Exception e
        ;; (stacktrace/print-stack-trace e)
        (log/errorf "dev system exception: %s"
                    (stacktrace/root-cause e) nil)))))

(defn stop
  "Stop system"
  []
  (alter-var-root #'system-var
    (when #'system-var
      (system/stop))))

(defn bounce
  "Take system down, bring back up, refresh repl"
  []
  (stop)
  (start)
  (refresh))
