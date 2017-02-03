(ns lcmap.clownfish.html
  "Define templates for various resources."
  (:require [cheshire.core :as json]
            [clj-time.format :as time-fmt]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [net.cgrand.enlive-html :as html]
            [camel-snake-kebab.core :as csk]))

(defn prep-for-html
  ""
  [source]
  (-> source
      (update :progress_at str)
      (update :progress_name str)
      (update :progress_desc str)))

(defn str-vals [kvs]
  ""
  (->> kvs
       (map (fn [[k v]] [k (str v)]))
       (into {})))

;; Used to produce navigation element, intended for use
;; with all templates.
(html/defsnippet nav "public/application.html"
  [:nav]
  []
  identity)

(html/deftemplate status-list "public/status.html"
  [services]
  [:nav] (html/content (nav))
  [:tbody :tr] (html/clone-for [[svc-name status] services]
                               [:.healthy] (html/content (-> status :healthy str))
                               [:.service] (html/content (name svc-name))
                               [:.message] (html/content (-> status :message str))))

(html/deftemplate default "public/application.html"
  [entity]
  [:nav] (html/content (nav))
  [:#debug] (html/content (json/encode entity {:pretty true})))
