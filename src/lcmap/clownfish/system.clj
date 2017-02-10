(ns lcmap.clownfish.system
  (:require [again.core :as again]
            [clojure.tools.logging :as log]
            ;; requiring this ensures the server component will start
            [lcmap.clownfish.server :as server]
            [lcmap.clownfish.setup.db]
            [lcmap.clownfish.setup.event]
            [mount.core :refer [defstate] :as mount]))

(defstate hook
  :start (do
           (log/debugf "registering shutdown handler")
           (.addShutdownHook (Runtime/getRuntime)
                             (Thread. #(mount/stop) "shutdown-handler"))))

(def default-retry-strategy (again/max-retries 10
                                               (again/constant-strategy 5000)))

(defn start
  ([environment]
   (start default-retry-strategy))

  ([environment startup-retry-strategy]
   (again/with-retries startup-retry-strategy
     (do (log/info "Stopping mount components")
         (mount/stop)
         (log/info "Starting mount components...")
         ;; insulation to make sure these states do not get accidentally
         ;; started 
         (mount/start-without #'lcmap.clownfish.setup.db/setup
                              #'lcmap.clownfish.setup.db/teardown
                              #'lcmap.clownfish.setup.event/setup
                              (mount/with-args {:environment environment}))))))
(defn stop []
  (mount/stop))
