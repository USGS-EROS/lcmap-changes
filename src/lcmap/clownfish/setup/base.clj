(ns lcmap.clownfish.setup.base
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))
          
(def config (edn/read-string (slurp (io/resource "setup.edn"))))
