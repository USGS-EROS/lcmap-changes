(ns lcmap.clownfish.server
  "Clownfish HTTP server related functions.

  This namespace provides functions and states for running the
  LCMAP-Changes REST API."
  (:require [cheshire.core :refer :all]
            [cheshire.generate :as json-gen :refer [add-encoder]]
            [compojure.core :refer :all]
            [clojure.tools.logging :as log]
            [langohr.basic :as lb]
            [langohr.consumers :as lcons]
            [lcmap.clownfish.config :refer [config]]
            [lcmap.clownfish.db :as db :refer [db-session]]
            [lcmap.clownfish.event :as event :refer [amqp-channel]]
            [lcmap.clownfish.middleware :refer [wrap-authenticate
                                                wrap-authorize
                                                wrap-exception
                                                wrap-request-debug]]
            [lcmap.clownfish.changes :as changes]
            [lcmap.clownfish.results :as results]
            [mount.core :refer [defstate] :as mount]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.accept :refer [wrap-accept]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]])

  (:import [org.joda.time.DateTime]
           [org.apache.commons.codec.binary Base64]))

;;; This is the REST API & AMQP listner entrypoint. All general middleware
;;; should be added here. Subordinate resources should be
;;; defined in other namespaces.

(defn make-handler
  "Build a middleware wrapped handler for app. This approach makes
  dependencies (components) available to handling functions."
  []
  (-> (changes/resource)
      (wrap-resource "public")
      (wrap-accept)
      (wrap-authorize)
      (wrap-authenticate)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-request-debug)
      (wrap-exception)))


;;; Server-related state

(defstate ring-handler
  :start (do
           (log/debugf "starting Ring handler")
           (make-handler)))

(defstate server
  :start (let [args (get-in config [:http])]
           (log/debugf "starting Jetty: %s" args)
           (run-jetty ring-handler args))
  :stop  (do
           (log/debugf "stopping Jetty")
           (.stop server)))


(defn handle-delivery
 [ch metadata payload]
 (let [change-result (event/decode-message metadata payload)]
   (log/debugf "deliver: %s" metadata)
   (log/debugf "content: %s" change-result)
   (results/save change-result)
   (lb/ack event/amqp-channel (metadata :delivery-tag))))

(defn handle-consume
 [consumer-tag]
 (log/debugf "consume ok: %s" consumer-tag))

(defstate listener
 :start (let [f {:handle-delivery-fn handle-delivery
                 :handle-consume-ok-fn handle-consume}
              queue (get-in config [:server :queue])
              listener-fn (lcons/create-default event/amqp-channel f)]
          (log/debugf "starting listener: %s" queue)
          (lb/consume event/amqp-channel queue listener-fn))
 :stop  (let []
          (log/debug "stopping listener: %s" listener)
          (lb/cancel event/amqp-channel listener)))

;; Encoders; turn objects into strings suitable for JSON responses.
(defn iso8601-encoder
  "Transform a Joda DateTime object into an ISO8601 string."
  [date-time generator]
  (.writeString generator (str date-time)))

(defn base64-encoder
  "Base64 encode a byte-buffer, usually raster data from Cassandra."
  [buffer generator]
  (log/debug "encoding HeapByteBuffer")
  (let [size (- (.limit buffer) (.position buffer))
        copy (byte-array size)]
    (.get buffer copy)
    (.writeString generator (Base64/encodeBase64String copy))))

(json-gen/add-encoder org.joda.time.DateTime iso8601-encoder)
(json-gen/add-encoder java.nio.HeapByteBuffer base64-encoder)
