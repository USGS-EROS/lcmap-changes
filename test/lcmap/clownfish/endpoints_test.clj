(ns lcmap.clownfish.endpoints-test
  "Tests from the end user's view"
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            ;; have to require server to include server state
            ;; otherwise it will never be started
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
  [http-host {:keys [algorithm x y]}]
  (log/spy :trace (req :get (str http-host "/results/" algorithm "/" x "/" y))))

;; test code
(deftest changes-health-resource
  (with-system
    (testing "health check"
      (let [resp (log/spy :trace (req :get (str http-host "/health")
                                      :headers {"Accept" "*/*"}))]
        (is (= 200 (:status resp)))))))

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

(deftest results
  (with-system
    ;; put a good alg in the system
    (upsert-algorithm http-host {:algorithm "test-alg"
                                 :enabled true
                                 :inputs_url_template
                                 "http://host/{algorithm}/{x}/{y}/{now}"})

    (testing "schedule non-existent algorithm"
      (let [body {:algorithm "does-not-exist" :x 123 :y 456}
            resp (get-results http-host body)]
        ;; should return unprocessable entity, HTTP 422
        (is (= 422 (:status resp)))))

    (testing "schedule existing algorithm, no results exist"
      (let [body       {:algorithm "test-alg" :x 123 :y 456}
            resp       (log/spy :debug (get-results http-host body))
            ticket     (json/decode (:body resp) true)
            expected   {:tile_x -585
                        :tile_y 2805
                        :x 123
                        :y 456
                        :tile_update_requested "2017-02-16T21:34:19.881Z"
                        :algorithm "test-alg"
                        :inputs_url "http://host/{algorithm}/{x}/{y}/{now}"
                        :refresh false
                        :algorithm-available true
                        :source-data-available true}]
        (is (= 202 (:status resp)))
        (is (= (type (:tile_update_requested ticket)) java.lang.String))
        (is (= (dissoc expected :tile_update_requested)
               (dissoc ticket :tile_update_requested)))))

    (testing "schedule same algorithm, get ticket")
    (testing "retrieve algorithm results once available")
    (testing "reschedule algorithm when results already exist")))
