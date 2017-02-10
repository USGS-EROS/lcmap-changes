(ns lcmap.clownfish.setup.db
  (:require [lcmap.clownfish.db :as db]
            [mount.core :as mount :refer [defstate]]))

(defstate setup
  :start (db/execute-cql "schema.setup.cql" db/db-cluster))

(defstate teardown
  :start (str "schema.teardown.cql")
  :stop (db/execute-cql teardown db/db-cluster))
