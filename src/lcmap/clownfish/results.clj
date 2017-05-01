(ns lcmap.clownfish.results
  (:require [clojure.tools.logging :as log]
            [digest]
            [lcmap.clownfish.db :as db]
            [lcmap.clownfish.algorithm :as alg]
            [lcmap.clownfish.state :refer [chip-specs]]
            [lcmap.clownfish.ticket :as ticket]
            [lcmap.commons.numbers :refer [numberize]]
            [lcmap.commons.chip :refer [snap]]
            [qbits.hayt :as hayt]))

(defn retrieve
  "Returns change results or nil"
  [{:keys [x y algorithm] :as data}]
  (let [[chip_x, chip_y] (snap x y (first chip-specs))]
    (->> (hayt/where [[= :chip_x chip_x]
                      [= :chip_y chip_y]
                      [= :algorithm algorithm]
                      [= :x x]
                      [= :y y]])
         (hayt/select :results)
         (db/execute)
         (first))))

(defn retrieve-chip
  "Return entire set of algorithm results containing x/y"
  [x y algorithm]
  (let [[chip_x, chip_y] (snap x  y (first chip-specs))]
    (->> (hayt/where [[= :chip_x chip_x]
                      [= :chip_y chip_y]
                      [= :algorithm algorithm]])
         (hayt/select :results)
         (db/execute)
         (into []))))

(defn save
  "Saves algorithm results"
  [{:keys [x y algorithm inputs_md5 result result_md5 result_ok result_produced] :as data}]
  (let [[chip_x, chip_y] (snap (int x) (int y) (first chip-specs))
        change-result {:chip_x (int chip_x)
                       :chip_y (int chip_y)
                       :x (int x)
                       :y (int y)
                       :algorithm algorithm
                       :inputs_md5 inputs_md5
                       :result result
                       :result_md5 result_md5
                       :result_ok result_ok
                       :result_produced result_produced}]
    (db/execute (hayt/insert :results (hayt/values change-result)))
    change-result))

;;; TODO - replace with implementation
(defn source-data-available?
  [{:keys [x y]}]
  true)

(defn schedule
  "Schedules algorithm execution while preventing duplicates"
  [{:keys [x y algorithm refresh] :as data}]
  (log/infof "scheduling: %s" data)
  (or (and (not refresh)
           (retrieve data))
      (ticket/create data)))

(defn get-results
  "HTTP request handler to get algorithm results"
  [algorithm x y {{r :refresh :or [r false]} :params :as req}]
  (log/tracef "get-changes :: params - %s" req)
  (let [data    {:x (numberize x)
                 :y (numberize y)
                 :algorithm algorithm
                 :refresh (Boolean/valueOf r)}
        results (retrieve data)]
    (log/tracef "get-changes results: %s" results)
    (if (and results (not (nil? (:result results))) (not (:refresh data)))
      (do (log/infof "returning results for %s" (dissoc data :refresh))
          {:status 200 :body (merge data results)})
      (let [src?   (future (source-data-available? data))
            alg?   (alg/enabled? (:algorithm data))
            valid? {:algorithm-available alg? :source-data-available @src?}]
        (if (not-every? true? (vals valid?))
          {:status 422 :body (merge data valid?)}
          {:status 202 :body (merge data valid? (schedule data))})))))

(defn get-results-chip
  "HTTP request handler; get all algorithm results for area that contains x/y"
  [algorithm-name {{:keys [x y]} :params :as req}]
  (log/tracef "get-results-chip: %s %s %s" algorithm-name x y)
  ;; The handler always returns a 200, even if there are not results.
  ;; This does not schedule processing. Also, using 404 doesn't seem
  ;; like the way to indicate nothing is found.
  (let [results (retrieve-chip (numberize x)
                               (numberize y)
                               algorithm-name)]
    {:status 200 :body results}))
