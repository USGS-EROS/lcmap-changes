(ns lcmap.clownfish.state
  (:require [mount.core :as mount]))

;;; TODO - query landsat/tile-specs/all
(defstate tile-specs
  ;:start {:tile_x 10 :tile_y 10 :shift_x 0 :shift_y 0})
   :start (slurp "http://localhost:5678/landsat/tile-specs"))
