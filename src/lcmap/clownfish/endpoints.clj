(ns lcmap.clownfish.endpoints
  (:require [camel-snake-kebab.core :refer [->snake_case_keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [compojure.core :refer :all]
            [lcmap.clownfish.algorithm :as alg]
            [lcmap.clownfish.results :as results]
            [lcmap.clownfish.health :as health]
            [lcmap.clownfish.html :as html]
            [lcmap.clownfish.middleware :refer [wrap-handler]]
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

;;;; Resources
(defn resource
  "HTTP Resource Handlers"
  []
  (wrap-handler
   (context "/" request
     (GET "/" []
       (with-meta
         {:status 200}
         {:template html/default}))

     (ANY "/" []
       (with-meta
         (allow ["GET"])
         {:template html/default}))

     (GET "/health" []
       (with-meta
         (health/check-health)
         {:template html/status-list}))

     (GET "/algorithms" []
       (with-meta
         (alg/get-algorithms)
         {:template html/default}))

     (GET "/algorithm/:algorithm{.+}" [algorithm]
       (with-meta
         (alg/get-algorithm algorithm)
         {:template html/default}))

     (PUT "/algorithm/:algorithm{.+}" [algorithm]
       (with-meta
         (alg/put-algorithm algorithm request)
         {:template html/default}))

     (GET "/results/:algorithm{.+}/:x{[0-9-]+}/:y{[0-9-]+}" [algorithm x y]
       (with-meta
         (results/get-results algorithm x y request)
         {:template html/default})))

   prepare-with respond-with))
