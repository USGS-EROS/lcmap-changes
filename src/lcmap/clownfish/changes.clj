(ns lcmap.clownfish.changes
  (:require [camel-snake-kebab.core :refer [->snake_case_keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [compojure.core :refer :all]
            [ring.util.accept :refer [accept]]
            [lcmap.clownfish.html :as html]
            [lcmap.clownfish.middleware :refer [wrap-handler]]))

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

;;; request handler helpers
(defn algorithm-available?
  [{:keys [algorithm]}]
  true)

(defn source-data-available?
  [{:keys [x y]}]
  true)

(defn change-results-exist?
  [{:keys [x y algorithm]}]
  true)

(defn query-data
  [{:keys [x y algorithm]}]
  {:algorithm algorithm
   :start  12343
   :end    45673
   :days   [443 441 322]
   :reds   [1 2 3]
   :blues  [1 2 3]
   :greens [1 2 3]
   :nirs   [1 2 3]
   :swir1s [1 2 3]
   :swir2s [1 2 3]
   :whatever ["the" "results" "are"]})

(defn run-tile
  [{:keys [x y algorithm] :as data}]
  (let [existing-ticket (snap-x-y-and-check-iwds data)]
    (if (some? existing-ticket)
      existing-ticket
      (enter-new-ticket-and-return))))

;;; request handlers
(defn get-changes
  [{{x :x
     y :y
     a :algorithm
     r :refresh
     :or {r false}} :params}]
  (let [data   {:x x :y y :algorithm a :refresh (boolean r)}
        alg?   (future (algorithm-available? data))
        valid? {:algorithm-available @alg?}])
  (if (not-every? true? (vals valid?))
    {:status 202 :body (merge data valid?)}
    (let [src?      (future (source-data-available? data))
          results?  {:change-results-exist (change-results-exist? data)}
          source?   {:source-data-available @src?}
          doquery?  (and (not (:refresh data))
                         (:change-results-exist results?))
          runtile?  (and (not doquery?)
                         (:source-data-available source?))
          body      (merge data valid? results? source? {:doquery doquery?
                                                         :runtile runtile?})]
      (cond
        doquery? {:status 202
                  :body (merge body {:changes (query-data data)})}
        runtile? {:status 202
                  :body (merge body {:ticket (run-tile data)})}
        :else    {:status 202
                  :body body}))))

;;; Routes
(defn resource
  "Handlers for landsat resource."
  []
  (wrap-handler
   (context "/changes" request
     (GET    "/" []
       (with-meta
         {:status 200}
         {:template html/default}))
     (ANY    "/" []
       (with-meta
         (allow ["GET"])
         {:template html/default}))
     (GET "/problem/" []
       {:status 200 :body "problem resource"}))
   prepare-with respond-with))
