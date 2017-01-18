(ns lcmap.clownfish.changes
  (:require [camel-snake-kebab.core :refer [->snake_case_keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [compojure.core :refer :all]
            [langohr.exchange :as le]
            [langohr.basic :as lb]
            [lcmap.commons.tile :as tile]
            [lcmap.clownfish.algorithm :as algorithm]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.event :refer [amqp-channel]]
            [lcmap.clownfish.html :as html]
            [lcmap.clownfish.middleware :refer [wrap-handler]]
            [mount.core :as mount :refer [defstate]]
            [qbits.hayt :as hayt]
            [ring.util.accept :refer [accept]]))

(defn allow [& verbs]
  (log/debug "explaining allow verbs")
  {:status 405
   :headers {"Allow" (str/join "," verbs)}})

;;; Request entity transformers.

(defn decode-json
  ""
  [body]
  (log/debug "req - decoding as JSON")
  (->> body
       (slurp)
       (json/decode)
       (transform-keys ->snake_case_keyword)))

(defn prepare-with
  "Request transform placeholder."
  [request]
  (log/debugf "req - prepare body: %s" (get-in request [:headers]))
  (if (= "application/json" (get-in request [:headers "content-type"]))
    (update request :body decode-json)
    request))

;;; Response entity transformers.

(defn to-html
  "Encode response body as HTML."
  [response]
  (log/debug "responding with HTML")
  (let [template-fn (:template (meta response) html/default)]
    (update response :body template-fn)))

(defn to-json
  "Encode response body as JSON."
  [response]
  (log/debug "responding with json")
  (-> response
      (update :body json/encode)
      (assoc-in [:headers "Content-Type"] "application/json")))

(def supported-types (accept "text/html" to-html
                             "application/json" to-json
                             "*/*" to-json))

(defn respond-with
  [request response]
  (supported-types request response))

(defn publish
  "Add ticket to queue for executing change detection."
  [ticket]
  (let [exchange (get-in config [:server :exchange])
        routing "change-detection"
        payload (json/encode ticket)]
    (log/debugf "publish '%s' ticket: %s" routing payload)
    (lb/publish amqp-channel exchange routing payload
                {:content-type "application/json"}))
  ticket)

;;; TODO - replace with implementation
(defn source-data-available?
  [{:keys [x y]}]
  true)

(defstate tile-specs
  :start {:tile_x 10 :tile_y 10 :shift_x 0 :shift_y 0})

;;; TODO - Fill in with proper tile-spec
(defn snap [x y]
  (tile/snap x y tile-specs))

(defn get-change-results
  "Returns change results or nil"
  [{:keys [x y algorithm] :as data}]
  (let [[tile_x, tile_y] (snap x y)]
    (->> (hayt/where [[= :tile_x tile_x]
                      [= :tile_y tile_y]
                      [= :algorithm algorithm]
                      [= :x x]
                      [= :y y]])
         (hayt/select :results)
         (db/execute)
         (first))))

(defn get-ticket
  "Retrieves existing ticket or nil."
  [{:keys [x y algorithm] :as data}]
  (dissoc (get-change-results data) :result :result_md5 :result_status
                                    :result_produced))

(defn create-ticket
  "Creates a new ticket for updating algorithm results.  Does not account for
   existing tickets."
  [{:keys [x y algorithm] :as data}]
  (let [[tile_x tile_y] (snap x y)
        ticket {:tile_x tile_x
                :tile_y tile_y
                :algorithm algorithm
                :x x
                :y y
                :tile_update_requested (str (time/now))
                :tile_update_began nil
                :tile_update_ended nil
                :inputs_url (inputs-url data)}]
    (->> ticket (publish)(hayt/values)(hayt/insert :results)(db/execute))
    ticket))

(defn schedule
  "Schedules algorithm execution while preventing duplicates"
  [{:keys [x y algorithm] :as data}]
  (or (get-ticket data)(create-ticket data)))

;;;; Request Handlers
;;; It is critical point to be made that as the code is currently structured,
;;; the parameters and return values of request handler functions
;;; control the versioned resource interface.  Care must be taken to not
;;; inadvertently alter the resource interface by changing either the
;;; parameters or returns.  Once clojure 1.9 is generally available, the
;;; interface will be able to be described via clojure.spec.  Until then,
;;; be careful!
(defn get-changes
  [{{x :x y :y a :algorithm r :refresh :or {r false}} :params}]
  (let [data    {:x x :y y :algorithm a :refresh (boolean r)}
        results (get-change-results data)]
       (if (and results (not (nil? (:result results))) (not (:refresh data)))
        {:status 200 :body (merge data {:changes results})}
        (let [src? (future (source-data-available? data))
              alg? (algorithm/available? data)
              valid? {:algorithm-available alg? :source-data-available? @src?}]
             (if (not-every? true? (vals valid?))
               {:status 422 :body (merge data valid?)}
               {:status 202 :body (merge data {:ticket (schedule data)})})))))

;;;; Resources
(defn resource
  "Handlers for changes resource."
  []
  (wrap-handler
   (context "/changes/v0-beta" request
     (GET "/" []
          (with-meta {:status 200}
            {:template html/default}))
     (GET "/:algorithm{.+}/:x{\\d.+}/:y{\\d.+}" []
          (with-meta (get-changes request)
            {:template html/default}))
     (ANY "/" []
          (with-meta (allow ["GET"])
            {:template html/default}))
     (GET "/problem/" []
          {:status 200 :body "problem resource"}))
   prepare-with respond-with))
