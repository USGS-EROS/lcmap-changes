(ns lcmap.clownfish.shared
  (:require [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [lcmap.clownfish.config :as config]
            [mount.core :as mount]
            [org.httpkit.client :as http]))

(def http-port (-> (slurp "test/resources/lcmap-changes.edn")
                   (edn/read-string)
                   (get-in [:http :port])))

(def http-host (str "http://localhost:" http-port))

(defmacro with-system
  "Start and stop the system, useful for integration tests."
  [& body]
  `(let [cfg# (edn/read-string (slurp (io/resource "lcmap-changes.edn")))]
     (log/debugf "starting test system with config: %s" cfg#)
     (mount/start (mount/with-args {:config cfg#}))
     (try
       (do ~@body)
       (finally
         (log/debug "Stopping test system")
         (mount/stop)))))

(defn req
  "Convenience function for making HTTP requests."
  [method url & {:keys [headers query-params form-params body]
                 :as   opts}]
  (let [defaults {:headers {"Accept" "application/json"}}]
    @(http/request (merge {:url url :method method} defaults opts))))
