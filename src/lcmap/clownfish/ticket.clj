(ns lcmap.clownfish.ticket
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.tools.logging :as log]
            [langohr.basic :as lb]
            [lcmap.clownfish.algorithm :as alg]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.event :refer [amqp-channel]]
            [lcmap.clownfish.state :refer [tile-specs]]
            [lcmap.commons.tile :refer [snap]]
            [qbits.hayt :as hayt]))

(defn announce
  "Add ticket to queue for executing change detection."
  [ticket]
  (let [exchange (get-in config [:server :exchange])
        routing "change-detection"
        payload (json/encode ticket)]
    (log/debugf "publish '%s' ticket: %s" routing payload)
    (lb/publish amqp-channel exchange routing payload
                {:content-type "application/json" :persistent true}))
  ticket)

(defn create
  "Creates a new ticket for updating algorithm results.  Does not account for
   existing tickets."
  [{:keys [x y algorithm] :as data}]
  (let [[tile_x tile_y] (snap x y (first tile-specs))
        ticket {:tile_x tile_x
                :tile_y tile_y
                :algorithm algorithm
                :x x
                :y y
                :tile_update_requested (str (time/now))
                :inputs_url (alg/inputs data)}]
    (->> ticket (announce)
         (hayt/values)
         (hayt/insert :results)
         (db/execute))
    (log/info "ticket created")
    (log/debugf "ticket created: %s" ticket)
    ticket))
