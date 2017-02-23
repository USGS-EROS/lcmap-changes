(ns lcmap.clownfish.specifications-test
  "Tests from the end user's view"
  (:require [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [langohr.basic :as lb]
            ;; have to require server to include server state
            ;; otherwise it will never be started
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.event :as event :refer [amqp-channel]]
            [lcmap.clownfish.server :as server]
            [lcmap.clownfish.shared :refer [with-system req]]))

(def json-header {"Accept" "application/json"
                  "Content-Type" "application/json"})

;; low level clients for resources
(defn upsert-algorithm
  [http-host {:keys [algorithm enabled inputs_url_template] :as all}]
  (req :put (str http-host "/algorithm/" algorithm)
       :body (json/encode all)
       :headers json-header))

(defn get-algorithm
  [http-host algorithm]
  (req :get (str http-host "/algorithm/" algorithm)))

(defn get-algorithms
  [http-host]
  (req :get (str http-host "/algorithms")))

(defn get-results
  "Retrieves results/tickets from lcmap-changes"
  [http-host {:keys [algorithm x y]}]
  (log/spy :trace (req :get (str http-host "/results/" algorithm "/" x "/" y))))

;; test code
(deftest changes-health-resource
  (with-system
    (testing "health check"
      (let [resp (log/spy :trace (req :get (str http-host "/health")
                                      :headers {"Accept" "*/*"}))]
        (is (= 200 (:status resp)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test algorithm(s) resource ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest algorithms
  (with-system
    (testing "no algorithms defined"
      (let [resp (log/spy :trace (req :get (str http-host "/algorithms")
                                      :headers {"Accept" "application/json"}))
            body (json/decode (:body resp))]
        (is (= 200 (:status resp)))
        (is (= 0 (count body)))
        (is (coll? body))))

    (testing "add algorithm - bad bodies"
        (doseq [body [{:enabled true :bad-inputs_url_template "http://host"}
                      {:not-enabled true :inputs_url_template "http://host"}
                      {:enabled "string" :inputs_url_template "http://host"}]]
            (let [response (req :put (str http-host "/algorithm/bad")
                                :body (json/encode body)
                                :headers json-header)
                  status (:status response)]
                (is (= 403 status)))))

    (testing "add good algorithm"
      (let [body   {:algorithm "good"
                    :enabled false
                    :inputs_url_template "http://host"}
            resp   (upsert-algorithm http-host body)]
          (is (= 202 (:status resp)))))

    (testing "update algorithm")
    (let [body {:algorithm "good"
                :enabled true
                :inputs_url_template "http://anotherhost"}
          resp (upsert-algorithm http-host body)]
        (is (= 202 (:status resp))))

    (testing "get algorithm"
      (let [resp     (get-algorithm http-host "good")
            expected {:algorithm "good"
                      :enabled true
                      :inputs_url_template "http://anotherhost"}]
        (is (= 200 (:status resp)))
        (is (= (json/decode (:body resp) true) expected))))

    (testing "get algorithms"
      (let [resp   (get-algorithms http-host)
            expected [{:algorithm "good"
                       :enabled true
                       :inputs_url_template "http://anotherhost"}]]
       (is (= 200 (:status resp)))
       (is (= (json/decode (:body resp) true) expected))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test results resource ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def timestamp-regex  "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z")
(def test-url-template "http://host/{{algorithm}}/{{x}}/{{y}}/{{now}}")

(defn test-url-regex
  "build regex for expected url"
  [ticket]
  (str "http://host/" (:algorithm ticket)
       "/" (:x ticket)
       "/" (:y ticket)
       "/" timestamp-regex))

(def ignored-keys [:tile_update_requested :tile_update_began :tile_update_ended
                   :inputs_url :result_produced])

(defn results-ok?
  "Compares two results (or tickets) minus inputs_url, tile_update_requested and
   result_produced which are non-deterministic due to timestamp information"
  [expected actual]
  (= (log/spy :trace (apply dissoc expected ignored-keys))
     (log/spy :trace (apply dissoc actual ignored-keys))))

(defn inputs-url-ok?
  "Determine if inputs_url conforms to expected structure after having
   been populated with template values by lcmap-changes"
  [ticket]
  (re-matches (re-pattern (test-url-regex ticket)) (:inputs_url ticket)))

(defn date-timestamp?
  "Determines if a val is a datetimestamp that conforms to expected structure"
  [ts]
  (re-matches (re-pattern timestamp-regex) ts))

(deftest results
  (with-system
    ;; put a good alg in the system
    (upsert-algorithm http-host {:algorithm "test-alg"
                                 :enabled true
                                 :inputs_url_template test-url-template})

    (testing "schedule non-existent algorithm"
      (let [body {:algorithm "does-not-exist" :x 123 :y 456}
            resp (get-results http-host body)]
        ;; should return unprocessable entity, HTTP 422
        (is (= 422 (:status resp)))))

    (testing "schedule existing algorithm, no results exist"
      (let [body       {:algorithm "test-alg" :x 123 :y 456}
            resp       (get-results http-host body)
            ticket     (json/decode (:body resp) true)
            expected   {:tile_x -585
                        :tile_y 2805
                        :x 123
                        :y 456
                        :tile_update_requested "{{now}} can't be determined"
                        :algorithm "test-alg"
                        :inputs_url "{{now}} can't be determined"
                        :refresh false
                        :algorithm-available true
                        :source-data-available true}]
        (is (= 202 (:status resp)))
        (is (date-timestamp? (:tile_update_requested ticket)))
        (is (inputs-url-ok? ticket))
        (is (results-ok? expected ticket))))

    (testing "schedule same algorithm, get ticket"
      (let [body     {:algorithm "test-alg" :x 123 :y 456}
            resp     (get-results http-host body)
            ticket   (json/decode (:body resp) true)
            expected {:tile_x -585
                      :tile_y 2805
                      :x 123
                      :y 456
                      :result_ok nil
                      :algorithm "test-alg"
                      :inputs_url "{{now}} can't be determined"
                      :refresh false
                      :algorithm-available true
                      :inputs_md5 nil
                      :source-data-available true
                      :result nil
                      :tile_update_requested "{{now}} can't be determined"
                      :tile_update_began nil
                      :tile_update_ended nil
                      :result_produced nil
                      :result_md5 nil}]
        (is (= 202 (:status resp)))
        (is (date-timestamp? (:tile_update_requested ticket)))
        (is (inputs-url-ok? ticket))
        (is (results-ok? expected ticket))))

    (testing "consume ticket from rabbitmq, send change-detection-response"
      (let [[metadata payload] (lb/get amqp-channel "unit.lcmap.changes.worker")
            body (event/decode-message metadata payload)
            expected {:tile_x -585
                      :tile_y 2805
                      :algorithm "test-alg"
                      :x 123
                      :y 456
                      :tile_update_requested "{{now}} can't be determined"
                      :inputs_url "{{now}} can't be determined"}]
        (is (date-timestamp? (:tile_update_requested body)))
        (is (inputs-url-ok? body))
        (is (results-ok? expected body))
        ;; send response to server exchange to mock up results.  The
        ;; change results should wind up in the db
        (lb/publish amqp-channel
                    "unit.lcmap.changes.worker"
                    "change-detection-result"
                    (->> {:inputs_md5 (digest/md5 "dummy inputs")
                          :result "some test results"
                          :result_md5 (digest/md5 "some test results")
                          :result_produced (tc/to-string (time/now))
                          :result_ok true}
                         (merge body)
                         (json/encode))
                    {:content-type "application/json" :persistent true})
        ;; sleep this for just a bit so the server has time to consume and
        ;; persist the message we just sent.
        (Thread/sleep 5)))

    (testing "retrieve algorithm results once available"
      (let [body     {:algorithm "test-alg" :x 123 :y 456}
            resp     (get-results http-host body)
            result   (json/decode (:body resp) true)
            expected {:tile_x -585
                      :tile_y 2805
                      :algorithm "test-alg"
                      :x 123
                      :y 456
                      :refresh false
                      :tile_update_requested "{{now}} can't be determined"
                      :tile_update_began "{{now}} can't be determined"
                      :tile_update_ended "{{now}} can't be determined"
                      :inputs_url "{{now}} can't be determined"
                      :inputs_md5 (digest/md5 "dummy inputs")
                      :result "some test results"
                      :result_md5 (digest/md5 "some test results")
                      :result_produced "{{now}} can't be determined"
                      :result_ok true}]
        (is (= 200 (:status resp)))
        (is (date-timestamp? (:tile_update_requested result)))
        (is (date-timestamp? (:result_produced result)))
        (is (results-ok? expected result))
        (is (= (set (keys expected))
               (set (keys result))))))

    (testing "reschedule algorithm when results already exist")))
