(ns lcmap.clownfish.state
  (:require [clojure.tools.logging :as log]
            [lcmap.clownfish.config :refer [config]]
            [mount.core :refer [defstate] :as mount]))

(defstate tile-specs
  ;:start {:tile_x 10 :tile_y 10 :shift_x 0 :shift_y 0})
   :start (let [url (get-in config [:state :tile-specs-url])]
              (log/info "Loading tile-specs...")
              (try
                (slurp url)
                (catch Exception e
                  (log/errorf e "Could not load tile-specs from: %s" url)
                  (throw e)))))
