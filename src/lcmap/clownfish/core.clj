(ns lcmap.clownfish.core
  "Functions for starting a server.

  See also:
  * `dev/lcmap/clownfish/user.clj` for REPL-driven development."
  (:require
   [clojure.tools.logging :as log]
   [lcmap.clownfish.system :as system])
  (:gen-class))

(def environment
  "Creates environment map from the system environment"
  {:http-port      (System/getenv "CLOWNFISH_HTTP_PORT")
   :event-host     (System/getenv "CLOWNFISH_RABBIT_HOST")
   :event-port     (System/getenv "CLOWNFISH_RABBIT_PORT")
   :db-keyspace    (System/getenv "CLOWNFISH_DB_KEYSPACE")
   :db-url         (System/getenv "CLOWNFISH_DB_CONTACT_POINTS")
   :db-user        (System/getenv "CLOWNFISH_DB_USERNAME")
   :db-pass        (System/getenv "CLOWNFISH_DB_PASSWORD")
   :exchange       (System/getenv "CLOWNFISH_EXCHANGE")
   :queue          (System/getenv "CLOWNFISH_QUEUE")
   :chip-specs-url (System/getenv "CLOWNFISH_CHIP_SPECS_URL")})

(defn -main
  "Start the server"
  [& args]
  (try
    (system/start environment)
    (catch Exception e
      (log/fatalf e "Could not start lcmap-changes... exiting")
      (System/exit 1))))
