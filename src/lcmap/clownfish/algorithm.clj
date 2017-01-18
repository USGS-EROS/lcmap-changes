(ns lcmap.clownfish.algorithm
  (require [cheshire.core :as json]
           [clojure.tools.logging :as log]
           [clojure.string :as str]
           [clostache.parser :as template]
           [lcmap.clownfish.db :as db]
           [qbits.hayt :as hayt]
           [schema.core :as schema]))

(def algorithm-schema
  {:algorithm schema/Str
   :enabled schema/Boolean
   :ubid_query schema/Str
   :tiles_url schema/Str})

(defn validate
   "Produce a map of errors if the algorithm is invalid, otherwise nil."
   [algorithm-definition]
   (schema/check algorithm-schema algorithm-definition))

(defn all
  "Retreives all algorithms."
  []
  (db/execute (hayt/select :algorithms)))

(defn upsert
  "Create/Update algorithm definition"
  [algorithm]
  (db/execute (hayt/insert :algorithms
                           (hayt/values algorithm))))

(defn configuration
  "Retrieves algorithm definition or nil"
  [{:keys [algorithm]}]
  (->> (hayt/where [[= :algorithm algorithm]])
       (hayt/select :algorithms)
       (db/execute)
       (first)))

(defn available?
  "Determines if an algorithm is defined and enabled in the system."
  [{:keys [algorithm] :as data}]
  (true? (:enabled (configuration data))))

;;;  :tile_url can be templated using Mustache syntax >= 1.0: {{target}}
;;;
;;;  Example:
;;; "http://host:5678/landsat/tiles
;;; ?x={{x}}&y={{y}}&acquired=2015-01-01/{{now}}{{#ubids}}&ubid={{.}}{{/ubids}}"
;;;
(defn inputs [{:keys [x y algorithm] :as data}]
  "Constructs url to retrieve tiles for algorithm input."
  (let [conf  (configuration data)
        ubids (json/decode (slurp (:ubid_query conf)))
        now   (tc/to-string (time/now))]
    (template/render (:tiles_url conf) (merge data {:ubids ubids
                                                    :now now}))))
