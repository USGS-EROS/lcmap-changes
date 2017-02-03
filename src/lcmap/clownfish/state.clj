(ns lcmap.clownfish.state
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [lcmap.clownfish.config :refer [config]]
            [mount.core :refer [defstate] :as mount]
            [org.httpkit.client :as http]))

(defmulti retrieve (fn [url]
                     (if (str/starts-with?
                          (str/lower-case url) "http")
                       :http
                       :local)))

(defmethod retrieve :http [url]
  @(http/request {:url url
                  :method :get
                  :headers {"Accept" "application/json"}}))

(defmethod retrieve :local [url]
  (-> url io/resource io/file slurp))

(defstate tile-specs
  ;:start {:tile_x 10 :tile_y 10 :shift_x 0 :shift_y 0})
  :start (let [url (get-in config [:state :tile-specs-url])]
           (log/debug "Loading tile-specs")
           (try
             (json/decode (retrieve url) true)
             (catch Exception e
               (log/errorf e "Could not load tile-specs from: %s" url)
               (throw e)))))
