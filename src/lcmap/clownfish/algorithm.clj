(ns lcmap.clownfish.algorithm
  (require [cheshire.core :as json]
           [clj-time.core :as time]
           [clj-time.coerce :as tc]
           [clojure.tools.logging :as log]
           [clojure.string :as str]
           [clostache.parser :as template]
           [lcmap.clownfish.db :as db]
           [qbits.hayt :as hayt]
           [schema.core :as sc]))

(def schema
  {:algorithm sc/Str
   :enabled   sc/Bool
   :ubids_url sc/Str
   :tiles_url sc/Str})

(defn validate
   "Produce a map of errors if the algorithm is invalid, otherwise nil."
   [algorithm-definition]
   (sc/check schema algorithm-definition))

(defn all
  "Retrieve all algorithms."
  []
  (db/execute (hayt/select :algorithms)))

(defn upsert
  "Create/Update algorithm definition"
  [algorithm]
  (db/execute (hayt/insert :algorithms
                           (hayt/values algorithm)))
  algorithm)

(defn configuration
  "Retrieve algorithm configuration or nil"
  [{:keys [algorithm]}]
  (->> (hayt/where [[= :algorithm algorithm]])
       (hayt/select :algorithms)
       (db/execute)
       (first)))

(defn available?
  "Determine if an algorithm is defined & enabled"
  [{:keys [algorithm] :as data}]
  (true? (:enabled (configuration data))))

(def get-ubids
  "Returns a sequence of ubids from supplied location."
  (memoize (fn [location]
             (log/debug "Loading ubids from: " location)
             (json/decode (slurp location)))))

;;;  :tile_url can be templated using Mustache syntax >= 1.0: {{target}}
;;;
;;;  Example:
;;; "http://host:5678/landsat/tiles
;;; ?x={{x}}&y={{y}}&acquired=2015-01-01/{{now}}{{#ubids}}&ubid={{.}}{{/ubids}}"
;;;
;;; Becomes:
;;; http://host:5678/landsat/tiles/x/y/acquired=2015-01-01/2017-01-18&ubids="ubid"
;;;
(defn inputs [{:keys [x y algorithm] :as data}]
  "Construct url to retrieve tiles for algorithm input"
  (let [conf  (configuration data)
        ubids (get-ubids (:ubids_url conf))
        now   (tc/to-string (time/now))]
    (template/render (:tiles_url conf) (merge data {:ubids ubids
                                                    :now now}))))
