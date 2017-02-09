(ns lcmap.clownfish.shared
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.system :as system]
            [org.httpkit.client :as http]))

(def http-port (get-in config [:http :port]))
(def http-host (str "http://localhost:" http-port))

(defmacro with-system
  "Start and stop the system, useful for integration tests."
  [& body]
  `(let [env#      (edn/read-string (slurp (io/resource "environment.edn")))
         strategy# (again/max-retries 1 (again/constant-strategy 5000))]
     (log/debugf "starting test system with environment: %s" env#)
     (try
       (system/start {:environment env#} strategy#)
       (catch Exception e#
         (log/errorf "Cannot start test system: %s" e#)
         (System/exit 1)))
     (try
       (do ~@body)
       (finally
         (log/debug "Stopping test system")
         (system/stop)))))

(defn req
  "Convenience function for making HTTP requests."
  [method url & {:keys [headers query-params form-params body]
                 :as   opts}]
  (let [defaults {:headers {"Accept" "application/json"}}]
    @(http/request (merge {:url url :method method} defaults opts))))
