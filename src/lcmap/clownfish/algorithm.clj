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
  (->> (hayt/select :algorithms)(db/execute)))

(defn configuration
  "Retrieves algorithm definition or nil"
  [{:keys [algorithm]}]
  ((first (db/execute)
          (hayt/select :algorithms (hayt/where [[= :algorithm algorithm]])))))

(defn available?
  "Determines if an algorithm is defined and enabled in the system."
  [{:keys [algorithm] :as data}]
  (true? (:enabled (configuration data))))

;;;  :tile_url can be templated using Mustache syntax >= 1.0: {{target}}
;;;  example: http://localhost:5678/landsat/tiles?x={{x}}&y={{y}}
(defn inputs-url [{:keys [x y algorithm] :as data}]
  "Constructs url to retrieve tiles for algorithm input."
  (let [conf  (configuration data)
        ubids (json/decode (slurp (:ubid_query conf)))
        url   (template/render (:tiles_url conf) data)]
    (str/join url #(map (str "&ubid=" %) (sort ubids)))))
