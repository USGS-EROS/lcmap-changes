(ns lcmap.clownfish.specifications-test
  "Tests from the end user's view"
  (:require [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [langohr.basic :as lb]
            [msgpack.core :as msgpack]
            ;; have to require server to include server state
            ;; otherwise it will never be started
            [lcmap.clownfish.configuration :refer [config]]
            [lcmap.clownfish.event :as event :refer [amqp-channel]]
            [lcmap.clownfish.server :as server]
            [lcmap.clownfish.shared :refer [with-system req]])
  (:import [org.apache.commons.codec.binary Base64]))

(def json-header {"Accept" "application/json"
                  "Content-Type" "application/json"})

(def message-pack-header {"Content-Type" "application/x-msgpack"})

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
  [http-host {:keys [algorithm x y refresh]}]
  (log/spy :debug (req :get
                       (str http-host
                            "/results/" algorithm
                            "/" x
                            "/" y
                            "?refresh=" (str refresh)))))

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
        (is (zero? (count body)))
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
        (is (= (json/decode (:body resp) true) expected))))

    (testing "disable algorithm")
    (let [body {:algorithm "good"
                :enabled false
                :inputs_url_template "http://anotherhost"}
          resp (upsert-algorithm http-host body)]
      (is (= 202 (:status resp))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test results resource ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def timestamp-regex  "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z")
(def test-url-template "http://host/{{algorithm}}/{{x}}/{{y}}/{{now}}")
(def test-algorithm "test-algorithm-1.0.0")

(defn test-url-regex
  "build regex for expected url"
  [ticket]
  (str "http://host/" (:algorithm ticket)
       "/" (:x ticket)
       "/" (:y ticket)
       "/" timestamp-regex))

(def ignored-keys [:tile_update_requested :inputs_url :result_produced])
(def test-algorithm-result (json/encode {:a "some" :b "result" :c 3}))

(defn results-ok?
  "Compares two results (or tickets) minus inputs_url, tile_update_requested and
   result_produced which are non-deterministic due to timestamp information"
  [expected actual]
  (= (log/spy :debug (apply dissoc expected ignored-keys))
     (log/spy :debug (apply dissoc actual ignored-keys))))

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
    (upsert-algorithm http-host {:algorithm test-algorithm
                                 :enabled true
                                 :inputs_url_template test-url-template})

    (testing "schedule non-existent algorithm"
      (let [body {:algorithm "does-not-exist" :x 123 :y 456 :refresh false}
            resp (get-results http-host body)]
        ;; should return unprocessable entity, HTTP 422
        (is (= 422 (:status resp)))))

    (testing "schedule existing algorithm, no results exist"
      (let [body       {:algorithm test-algorithm :x 123 :y 456 :refresh false}
            resp       (get-results http-host body)
            ticket     (json/decode (:body resp) true)
            expected   {:tile_x -585
                        :tile_y 2805
                        :x 123
                        :y 456
                        :tile_update_requested "{{now}} can't be determined"
                        :algorithm test-algorithm
                        :inputs_url "{{now}} can't be determined"
                        :refresh false
                        :algorithm-available true
                        :source-data-available true}]
        (is (= 202 (:status resp)))
        (is (date-timestamp? (:tile_update_requested ticket)))
        (is (inputs-url-ok? ticket))
        (is (results-ok? expected ticket))))

    (testing "schedule same algorithm, get ticket"
      (let [body     {:algorithm test-algorithm :x 123 :y 456 :refresh false}
            resp     (get-results http-host body)
            ticket   (json/decode (:body resp) true)
            expected {:tile_x -585
                      :tile_y 2805
                      :x 123
                      :y 456
                      :result_ok nil
                      :algorithm test-algorithm
                      :inputs_url "{{now}} can't be determined"
                      :refresh false
                      :algorithm-available true
                      :inputs_md5 nil
                      :source-data-available true
                      :result nil
                      :tile_update_requested "{{now}} can't be determined"
                      :result_produced nil
                      :result_md5 nil}]
        (is (= 202 (:status resp)))
        (is (date-timestamp? (:tile_update_requested ticket)))
        (is (inputs-url-ok? ticket))
        (is (results-ok? expected ticket))))

    (testing "consume ticket from rabbitmq, send change-detection-response"
      (let [[metadata payload] (lb/get amqp-channel test-algorithm)
            body (event/unpack-message metadata payload)
            expected {:tile_x -585
                      :tile_y 2805
                      :algorithm test-algorithm
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
                    test-algorithm
                    test-algorithm
                    (->> {:inputs_md5 (digest/md5 "dummy inputs")
                          :result  test-algorithm-result
                          :result_md5 (digest/md5 (str test-algorithm-result))
                          :result_produced (tc/to-string (time/now))
                          :result_ok true}
                         (merge body)
                         (walk/stringify-keys)
                         (msgpack/pack))
                    {:content-type "application/x-msgpack" :persistent true})
        ;; sleep this for just a bit so the server has time to consume and
        ;; persist the message we just sent.
        (Thread/sleep 1000)))

    (testing "retrieve algorithm results once available"
      (let [body     {:algorithm test-algorithm :x 123 :y 456 :refresh false}
            resp     (get-results http-host body)
            result   (json/decode (:body resp) true)
            expected {:tile_x -585
                      :tile_y 2805
                      :algorithm test-algorithm
                      :x 123
                      :y 456
                      :refresh false
                      :tile_update_requested "{{now}} can't be determined"
                      :inputs_url "{{now}} can't be determined"
                      :inputs_md5 (digest/md5 "dummy inputs")
                      :result test-algorithm-result
                      :result_md5 (digest/md5 (str test-algorithm-result))
                      :result_produced "{{now}} can't be determined"
                      :result_ok true}]
        (is (= 200 (:status resp)))
        (is (date-timestamp? (:tile_update_requested result)))
        (is (date-timestamp? (:result_produced result)))
        (is (results-ok? expected result))
        (is (= (set (keys expected))
               (set (keys result))))))

    (testing "retrieve algorithm results for tile"
      (let [algorithm test-algorithm
            uri       (str http-host "/results/" algorithm "/tile")
            params    {:x 123 :y 456}
            resp      (req :get uri :query-params params)
            results   (json/decode (resp :body) true)
            expected  {:tile_x -585
                       :tile_y 2805
                       :algorithm test-algorithm
                       :x 123
                       :y 456
                       :refresh false
                       :tile_update_requested "{{now}} can't be determined"
                       :inputs_url "{{now}} can't be determined"
                       :inputs_md5 (digest/md5 "dummy inputs")
                       :result test-algorithm-result
                       :result_md5 (digest/md5 (str test-algorithm-result))
                       :result_produced "{{now}} can't be determined"
                       :result_ok true}]
        (is (= 200 (:status resp)))
        (is (every? (fn [actual] results-ok? expected actual) results))))

    (testing "reschedule algorithm when results already exist"
      (let [resp1  (get-results http-host {:algorithm test-algorithm
                                           :x 123 :y 456 :refresh false})
            ts1    (:tile_update_requested (json/decode (:body resp1) true))
            resp2  (get-results http-host {:algorithm test-algorithm
                                           :x 123 :y 456 :refresh true})
            result (json/decode (:body resp2) true)]
        (is (= 202 (:status resp2)))
        (is (date-timestamp? ts1))
        (is (date-timestamp? (:tile_update_requested result)))
        (is (not (= ts1 (:tile_update_requested result))))
        (is (:refresh result))))

    (testing "deleting exchanges and queues"
      ;;; TODO - This needs to be in a fixture teardown
      ;;; clear the test-alg queue of any lingering messages first
      (lb/get amqp-channel test-algorithm)
      (event/destroy-queue test-algorithm)
      (event/destroy-exchange test-algorithm))))
