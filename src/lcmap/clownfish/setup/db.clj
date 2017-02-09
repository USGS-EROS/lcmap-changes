(ns lcmap.clownfish.setup.db
  (:require [lcmap.clownfish.db :as db]))

(defn setup
  []
  (db/execute-cql "schema.setup.cql" db/db-cluster))

(defn teardown
  []
  (db/execute-cql "schema.teardown.cql" db/db-cluster))
