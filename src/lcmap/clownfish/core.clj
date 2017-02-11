(ns lcmap.clownfish.core
  "Functions for starting a server, worker, or both.

  The server is an HTTP handler and the worker is an AMQP consumer.

  Both modes of operation can be run in a single process, although
  in a production environment they should be run separately so that
  each can be scaled independently to handle varying workloads.

  See also:
  * `dev/lcmap/clownfish/user.clj` for REPL-driven development."
  (:require
            [clojure.tools.logging :as log]
            [lcmap.clownfish.system :as system])
  (:gen-class))

(def environment
 "Creates environment map from the system environment"
 {:http-port      (System/getenv "LC_HTTP_PORT")
  :event-host     (System/getenv "LC_RABBIT_HOST")
  :event-port     (System/getenv "LC_RABBIT_PORT")
  :db-keyspace    (System/getenv "LC_DB_KEYSPACE")
  :db-url         (System/getenv "LC_DB_CONTACT_POINTS")
  :db-user        (System/getenv "LC_DB_USERNAME")
  :db-pass        (System/getenv "LC_DB_PASSWORD")
  :exchange       (System/getenv "LC_EXCHANGE")
  :queue          (System/getenv "LC_QUEUE")
  :tile-specs-url (System/getenv "LC_TILE_SPECS_URL")})


(defn -main
  "Start the server"
  [& args]
  (try
    (system/start environment)
    (catch Exception e
      (log/fatalf e "Could not start lcmap-changes... exiting")
      (System/exit 1))))
