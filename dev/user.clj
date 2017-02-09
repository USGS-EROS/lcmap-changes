(ns user
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [lcmap.clownfish.event :as event]
            [lcmap.clownfish.setup.base :as setup-base]
            [lcmap.clownfish.setup.db]
            [lcmap.clownfish.setup.event]
            [lcmap.clownfish.system :as system]
            [mount.core :as mount]))

(def system_var nil)
(def retry-strategy (again/max-retries 0 (again/constant-strategy 0)))

(defn init-db
  "Manual operation to set up a db schema."
  []
  (lcmap.clownfish.setup.db/setup))

(defn init-event
  "Manual operation to set up rabbit queues, exchanges and bindings."
  []
  (lcmap.clownfish.setup.event/setup
    (merge setup-base/config (:amqp-channel (event/amqp-channel)))))

(defn start
  []
  (alter-var-root #'system_var
    (try
      (system/start (edn/read-string (slurp (io/resource "environment.edn")))
       retry-strategy)
      (catch Exception e
        ;; (stacktrace/print-stack-trace e)
        (log/errorf "dev system exception: %s" (stacktrace/root-cause e) nil)))))

(defn stop
  "Stop system"
  []
  (alter-var-root #'system_var
    (when system_var
      (system/stop))))

(defn bounce
  "Take system down, bring back up, refresh repl"
  []
  (stop)
  (start)
  (refresh))
