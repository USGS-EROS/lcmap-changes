(ns lcmap.clownfish.specifications-test
  "Tests from the end user's view"
  (:require [clojure.java.io :as io]
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

(defn tickets-ok?
  "Compares two tickets minus inputs_url and tile_update_requested, which
   are non-deterministic due to timestamp information"
  [expected actual]
  (= (dissoc expected :tile_update_requested :inputs_url)
     (dissoc actual :tile_update_requested :inputs_url)))

(defn inputs-url-ok?
  "Determine if inputs_url conforms to expected structure after having
   been populated with template values by lcmap-changes"
  [ticket]
  (re-matches (re-pattern (test-url-regex ticket)) (:inputs_url ticket)))

(defn tile-update-requested-ok?
  "Determines if the tile_update_requested field conforms to expected structure"
  [ticket]
  (re-matches (re-pattern timestamp-regex) (:tile_update_requested ticket)))

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
        (is (tile-update-requested-ok? ticket))
        (is (inputs-url-ok? ticket))
        (is (tickets-ok? expected ticket))))

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
        (is (tile-update-requested-ok? ticket))
        (is (inputs-url-ok? ticket))
        (is (tickets-ok? expected ticket))))

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
        (is (tile-update-requested-ok? body))
        (is (inputs-url-ok? body))
        (is (tickets-ok? expected body))
        ;; send response to server exchange to mock up results.  The
        ;; change results should wind up in the db
        (lb/publish amqp-channel
                    "unit.lcmap.changes.worker"
                    "change-detection-result"
                    (json/encode (merge body {:results "some test results"
                                              :results_ok true}))
                    {:content-type "application/json" :persistent true})
        ;; sleep this for just a bit so the server has time to consume and
        ;; persist the message we just sent.
        (Thread/sleep 5)))

    (testing "retrieve algorithm results once available")
    (testing "reschedule algorithm when results already exist")))
