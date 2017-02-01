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
   :inputs_url_template sc/Str})

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

;;;  :tile_url can be templated using Mustache syntax >= 1.0: {{target}}
;;;
;;;  Example:
;;; "http://host:5678/landsat/tiles
;;; ?x={{x}}&y={{y}}&acquired=2015-01-01/{{now}}&ubid="ubid1"&ubid="ubid2"
;;;
;;; Becomes:
;;; http://host:5678/landsat/tiles?x=x&y=y&acquired=2015-01-01/2017-01-18&ubid="ubid1"&ubid="ubid2"
;;;
(defn inputs [{:keys [x y algorithm] :as data}]
  "Construct url to retrieve tiles for algorithm input"
  (let [conf  (configuration data)
        now   (tc/to-string (time/now))]
    (template/render (:inputs_url_template conf) (merge data {:now now}))))
