(ns lcmap.clownfish.configuration
  "Supplies system configuration map"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dire.core :as dire :refer [with-handler!]]
            [lcmap.commons.numbers :refer [numberize]]
            [mount.core :refer [defstate] :as mount]))

(defn- tokenize
  "Split a string on whitespace and returns a vector of the tokens or nil if
   the value was untokenizable"
  [value]
  (vec (remove #(zero? (count %)) (str/split value #" "))))

(with-handler! #'tokenize
  [java.lang.NullPointerException java.lang.ClassCastException]
  (fn [e & args] nil))

(defn config-map
  [env]
  {:http   {:port (numberize (:http-port env))
            :join? false :daemon? true}
   :event  {:host (:event-host env)
            :port (numberize (:event-port env))}
   :db     {:keyspace (:db-keyspace env)
            :cluster {:contact-points (tokenize (:db-url env))
                      :credentials {:user (:db-user env)
                                    :password (:db-pass env)}
                      :query-options {:consistency :quorum}}}
   :server {:exchange (:exchange env) :queue (:queue env)}
   :chip-specs-url (:chip-specs-url env)})

(defstate config
  :start (log/spy :debug (config-map ((mount/args) :environment))))
