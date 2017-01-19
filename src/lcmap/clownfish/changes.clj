(ns lcmap.clownfish.changes
  (:require [camel-snake-kebab.core :refer [->snake_case_keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [compojure.core :refer :all]
            [lcmap.clownfish.algorithm :as alg]
            [lcmap.clownfish.config :refer [config]]
            [lcmap.clownfish.html :as html]
            [lcmap.clownfish.middleware :refer [wrap-handler]]
            [lcmap.clownfish.results :as change-results]
            [lcmap.clownfish.ticket :as ticket]
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

(def supported-types (accept :default to-json
                             "application/json" to-json
                             "text/html" to-html))

(defn respond-with
  [request response]
  (supported-types request response))

;;; TODO - replace with implementation
(defn source-data-available?
  [{:keys [x y]}]
  true)

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
        results (change-results/retrieve data)]
    (if (and results (not (nil? (:result results))) (not (:refresh data)))
      {:status 200 :body (merge data {:changes results})}
      (let [src?   (future (source-data-available? data))
            alg?   (alg/available? data)
            valid? {:algorithm-available alg? :source-data-available? @src?}]
        (if (not-every? true? (vals valid?))
          {:status 422 :body (merge data valid?)}
          {:status 202 :body (merge data {:ticket (ticket/schedule data)})})))))

(defn get-algorithms
  "Returns all algorithms defined in the system."
  []
  {:status 200 :body (alg/all)})

(defn get-algorithm
  "Returns an algorithm if defined in the system."
  [algorithm]
  (let [result (alg/configuration {:algorithm algorithm})]
    (if result
      ({:status 200 :body result})
      ({:status 404 :body (str algorithm " not found.")}))))

(defn put-algorithm
  "Updates or creates an algorithm definition"
  [algorithm {body :body}]
  (let [alg-def (merge {:algorithm algorithm} body)]
    (or (some->> (alg/validate alg-def)
                 (assoc {:status 403} :body))
        (some->> (alg/upsert alg-def)
                 (assoc {:status 202} :body)))))

;;;; Resources
(defn resource
  "Handlers for changes resource."
  []
  (wrap-handler
   (context "/changes/v0" request
     (GET "/" []
          (with-meta {:status 200} {:template html/default}))

     (GET "/algorithms" []
          (with-meta (get-algorithms) {:template html/default}))

     (GET "/algorithm/:algorithm{.+}" [algorithm]
          (with-meta (get-algorithm algorithm) {:template html/default}))

     (PUT "/algorithm/:algorithm{.+}" [algorithm]
          (with-meta (put-algorithm algorithm request)
            {:template html/default}))

     (GET "/:algorithm{.+}/:x{\\d.+}/:y{\\d.+}" []
          (with-meta (get-changes request) {:template html/default}))

     (ANY "/" []
          (with-meta (allow ["GET"]) {:template html/default}))

     (GET "/problem/" []
          {:status 200 :body "problem resource"}))

   prepare-with respond-with))
