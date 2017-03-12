(ns lcmap.clownfish.setup.finalize
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :refer [db-cluster]]
            [lcmap.clownfish.event :as event]
            [lcmap.clownfish.setup.cassandra]
            [mount.core :as mount]))

(def system-finalization nil)
(def retry-strategy (again/max-retries 0 (again/constant-strategy 0)))

(defn cassandra
  "Manual operation to set up a db schema."
  [environment]
  (try
    (alter-var-root #'system-finalization
                    (mount/start #'lcmap.clownfish.configuration/config
                                 #'lcmap.clownfish.db/db-cluster
                                 #'lcmap.clownfish.setup.cassandra/teardown
                                 (mount/with-args {:environment environment}))
                    (mount/stop  #'lcmap.clownfish.configuration/config
                                 #'lcmap.clownfish.db/db-cluster
                                 #'lcmap.clownfish.setup.cassandra/teardown))
    (catch Exception e
      (stacktrace/print-stack-trace e)
      (log/errorf "error initializing db: %s" (stacktrace/root-cause e)))))
