(ns lcmap.clownfish.simple
  "Supplies system config map and defaults"
  (:require [clojure.string :as str]
            [dire.core :as dire :refer [with-handler!]]))

(defn- getenv
  "Aesthetics only"
  [value]
  (System/getenv value))

(defn- tokenize
  "Split a string on whitespace and returns a vector of the tokens or nil if
   the value was untokenizable"
  [value]
  (vec (remove #(= 0 (count %)) (str/split value #" "))))

(with-handler! #'tokenize
  [java.lang.NullPointerException java.lang.ClassCastException]
  (fn [e & args] nil))

(def envs
  {:http-port      (or (getenv "CLOWNFISH_HTTP_PORT") 5778)
   :event-host     (or (getenv "CLOWNFISH_RABBIT_HOST") "localhost")
   :event-port     (or (getenv "CLOWNFISH_RABBIT_PORT") 5672)
   :db-keyspace    (or (getenv "CLOWNFISH_DB_KEYSPACE") "lcmap_changes_local")
   :db-url         (or (getenv "CLOWNFISH_DB_CONTACT_POINTS") "localhost")
   :db-user        (or (getenv "CLOWNFISH_DB_USERNAME") "guest")
   :db-pass        (or (getenv "CLOWNFISH_DB_PASSWORD") "guest")
   :exchange       (or (getenv "CLOWNFISH_EXCHANGE") "local.lcmap.changes.server")
   :queue          (or (getenv "CLOWNFISH_QUEUE") "local.lcmap.change.server")
   :tile-specs-url (or (getenv "CLOWNFISH_TILE_SPECS_URL") "tile-specs.json")})

(def config
  {:http   {:port (:http-port envs) :join? false :daemon? true}
   :event  {:host (:event-host envs):port (:event-port envs)}
   :db     {:keyspace (:db-keyspace envs)
            :cluster {:contact-points (tokenize (:db-url envs))
                      :credentials {:user (:db-user envs)
                                    :password (:db-pass envs)}}}
   :server {:exchange (:exchange envs) :queue (:queue envs)}
   :tile-specs-url (:tile-specs-url envs)
   :fixtures {:queues [{:name "local.lcmap.changes.server"
                        :opts {:durable true :exclusive false
                               :auto-delete false}}
                       {:name "local.lcmap.changes.worker"
                        :opts {:durable true :exclusive false
                               :auto-delete false}}]
              :exchanges [{:name "local.lcmap.changes.server"
                           :type "topic" :opts {:durable true}}
                          {:name "local.lcmap.changes.worker"
                           :type "topic" :opts {:durable true}}]
              :bindings [{:exchange "local.lcmap.changes.server"
                          :queue "local.lcmap.changes.worker"
                          :opts {:routing-key "change-detection"}}
                         {:exchange "local.lcmap.changes.worker"
                          :queue "local.lcmap.changes.server"
                          :opts {:routing-key "change-detection-result"}}]}})
