(ns lcmap.clownfish.setup.cassandra
  (:require [lcmap.clownfish.db :as db]
            [mount.core :as mount :refer [defstate]]))

(defstate setup
  :start (db/execute-cql "cassandra.setup.cql" db/db-cluster))

(defstate teardown
  :start (str "cassandra.teardown.cql")
  :stop (db/execute-cql teardown db/db-cluster))
