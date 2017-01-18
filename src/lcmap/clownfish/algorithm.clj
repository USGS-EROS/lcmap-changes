(ns lcmap.clownfish.algorithm
  (require [cheshire.core :as json]
           [clojure.tools.logging :as log]
           [clojure.string :as str]
           [clostache.parser :as template]
           [lcmap.clownfish.db :as db]
           [qbits.hayt :as hayt]))

(defn all
  "Retreives all algorithms."
  []
  (db/execute (hayt/select :algorithms)))

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
;;;  example: http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}
;;; "http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}{{#ubids}}&ubid={{.}}{{/ubids}}"
(defn inputs [{:keys [x y algorithm] :as data}]
  "Constructs url to retrieve tiles for algorithm input."
  (let [conf  (configuration data)
        ubids (json/decode (slurp (:ubid_query conf)))]
    (template/render (:tiles_url conf) (merge data {:ubids ubids}))))
