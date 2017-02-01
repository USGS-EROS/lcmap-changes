(ns lcmap.clownfish.results
  (:require [lcmap.clownfish.db :as db]
            [lcmap.clownfish.state :refer [tile-specs]]
            [lcmap.commons.tile :refer [snap]]
            [qbits.hayt :as hayt]))

(defn retrieve
  "Returns change results or nil"
  [{:keys [x y algorithm] :as data}]
  (let [[tile_x, tile_y] (snap x y (first tile-specs))]
    (->> (hayt/where [[= :tile_x tile_x]
                      [= :tile_y tile_y]
                      [= :algorithm algorithm]
                      [= :x x]
                      [= :y y]])
         (hayt/select :results)
         (db/execute)
         (first))))

(defn save
  "Saves algorithm results"
  [{:keys [x y algorithm result result_md5 result_status result_produced] :as data}]
  (let [[tile_x, tile_y] (snap x y (first tile-specs))
        change-result {:tile_x tile_x
                       :tile_y tile_y
                       :x x
                       :y y
                       :algorithm algorithm
                       :result result
                       :result_md5 result_md5
                       :result_status result_status
                       :result_produced result_produced}]
      (db/execute (hayt/insert :results (hayt/values change-result)))

    change-result))
