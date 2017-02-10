(ns user
  (:require [again.core :as again]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [lcmap.clownfish.system :as system]))

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
