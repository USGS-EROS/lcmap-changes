(ns lcmap.clownfish.ticket
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [langohr.basic :as lb]
            [lcmap.clownfish.algorithm :as alg]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.event :refer [amqp-channel]]
            [lcmap.clownfish.state :refer [chip-specs]]
            [lcmap.commons.chip :refer [snap]]
            [msgpack.core :as msgpack]
            [qbits.hayt :as hayt]))

(defn announce
  "Add ticket to queue for executing change detection."
  [ticket]
  (let [exchange (get-in config [:server :exchange])
        routing (:algorithm ticket)
        payload (msgpack/pack (walk/stringify-keys ticket))]
    (log/debugf "publish '%s' ticket: %s" routing payload)
    (lb/publish amqp-channel exchange routing payload
                {:content-type "application/x-msgpack" :persistent true}))
  ticket)

(defn create
  "Creates a new ticket for updating algorithm results.  Does not account for
   existing tickets."
  [{:keys [x y algorithm] :as data}]
  (let [[chip_x chip_y] (snap x y (first chip-specs))
        ticket {:chip_x chip_x
                :chip_y chip_y
                :algorithm algorithm
                :x x
                :y y
                :chip_update_requested (tc/to-string (time/now))
                :inputs_url (alg/inputs data)}]
    (announce ticket)
    (->> (update ticket :chip_update_requested #(tc/to-long %))
         (hayt/values)
         (hayt/insert :results)
         (db/execute))
    (log/info "ticket created")
    (log/debugf "ticket created: %s" ticket)
    ticket))
