(ns lcmap.clownfish.state
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [lcmap.clownfish.configuration :refer [config]]
            [mount.core :refer [defstate] :as mount]
            [org.httpkit.client :as http]))

(defmulti retrieve (fn [url]
                     (if (str/starts-with?
                          (str/lower-case url) "http")
                       :http
                       :local)))

(defmethod retrieve :http [url]
  (:body
   @(http/request
     {:url url, :method :get, :headers {"Accept" "application/json"}})))

(defmethod retrieve :local [url]
  (-> url io/resource slurp))

(defstate chip-specs
  ;:start {:chip_x 10 :chip_y 10 :shift_x 0 :shift_y 0})
  :start (let [url (:chip-specs-url config)]
           (log/debugf "Loading chip-specs from: %s" url)
           (try
             (-> url retrieve (json/decode true))
             (catch Exception e
               (log/errorf e "Could not load chip-specs from: %s" url)
               (throw e)))))
