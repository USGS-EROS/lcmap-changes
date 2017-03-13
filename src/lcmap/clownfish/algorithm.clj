(ns lcmap.clownfish.algorithm
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clostache.parser :as template]
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.event :as event]
            [qbits.hayt :as hayt]
            [schema.core :as sc]))

(defrecord AlgorithmConfig [algorithm enabled inputs_url_template])

(def schema
  {:algorithm sc/Str
   :enabled   sc/Bool
   :inputs_url_template sc/Str})

(defn validate
  "Produce a map of errors if the algorithm is invalid, otherwise nil."
  [^AlgorithmConfig Alg]
  (sc/check schema Alg))

(defn all
  "Retrieve all algorithms."
  []
  (db/execute (hayt/select :algorithms)))

(defn upsert
  "Create/Update algorithm definition"
  [^AlgorithmConfig Alg]
  (db/execute (hayt/insert :algorithms
                           (hayt/values Alg)))
  Alg)

(defn query
  "Retrieve algorithm configuration or nil"
  [algorithm]
  (->> (hayt/where [[= :algorithm algorithm]])
       (hayt/select :algorithms)
       (db/execute)
       (first)))

(defn enabled?
  "Determine if an algorithm is enabled"
  [algorithm]
  (true? (:enabled (query algorithm))))

(defn disabled?
  "Determine if an algorithm is disabled"
  [algorithm]
  (false? (:enabled (query algorithm))))

(defn defined?
  "Determine if an algorithm is defined"
  [algorithm]
  (nil? (query algorithm)))

(defn enable
  "Enables an algorithm in the system."
  ;;; TODO - Autodeploy mesos dockerimage, make sure dockerimage exists
  ;;; server queue & exchange are created when the listener starts in server.clj
  [algorithm]
  (let [[queue exchange] ((juxt event/create-queue
                                event/create-exchange) algorithm)
        server-exchange (get-in config [:server :exchange])
        server-queue (get-in config [:server :queue])]
    (event/create-binding (:name queue)
                          server-exchange
                          {:routing-key algorithm})
    (event/create-binding server-queue
                          (:name exchange)
                          {:routing-key algorithm})))

(defn disable
  "Disables an algorithm in the system"
  [algorithm]
  (->> algorithm
       (event/destroy-exchange)
       (event/destroy-queue)))

;;;  :inputs_url_template can be templated using Mustache syntax >= 1.0: {{target}}
;;;
;;;  Example:
;;; "http://host:5678/landsat/tiles
;;; ?x={{x}}&y={{y}}&acquired=2015-01-01/{{now}}&ubid="ubid1"&ubid="ubid2"
;;;
;;; Becomes:
;;; http://host:5678/landsat/tiles?x=x&y=y&acquired=2015-01-01/2017-01-18&ubid="ubid1"&ubid="ubid2"
;;;
(defn inputs
  "Construct url to retrieve tiles for algorithm input"
  [{:keys [x y algorithm] :as data}]
  (let [conf  (query algorithm)
        now   (tc/to-string (time/now))]
    (template/render (:inputs_url_template conf) (merge data {:now now}))))

(defn get-algorithms
  "Returns all algorithms defined in the system."
  []
  (log/debugf "get-algorithms...")
  {:status 200 :body (all)})

(defn get-algorithm
  "Returns an algorithm if defined in the system."
  [algorithm]
  (log/debugf "get-algorithm: %s..." algorithm)
  (let [result (query algorithm)]
    (if result
      {:status 200 :body result}
      {:status 404 :body (str algorithm " not found.")})))

(defn put-algorithm
  "Updates or creates an algorithm definition"
  [algorithm {body :body}]
  (log/debugf "put-algorithm: %s..." algorithm)
  (let [Alg (map->AlgorithmConfig (merge {:algorithm algorithm} body))]
    (or (some->> (validate Alg)
                 (str)
                 (assoc {:status 403} :body))
        (do
          (if (:enabled Alg)
            (enable algorithm)
            (disable algorithm))
          (assoc {:status 202} :body (upsert Alg))))))
