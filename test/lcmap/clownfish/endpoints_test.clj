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

(deftest changes-health-resource
  (with-system
    (testing "health check"
      (let [resp (log/spy (req :get (str http-host "/health")
                               :headers {"Accept" "*/*"}))]
        (is (= 200 (:status resp)))))))

(deftest algorithms
  (with-system
    (testing "no algorithms defined"
      (let [resp (log/spy :debug (req :get (str http-host "/algorithms")
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
                (when (not (contains? #{403 500} status))
                    (log/errorf "Error in add algorithm-bad bodies")
                    (log/errorf "Response:%s" response))
                (is (contains? #{403 500} status)))))

    (testing "add good algorithm"
      (let [body   {:algorithm "good"
                    :enabled false
                    :inputs_url_template "http://host"}
            resp   (upsert-algorithm http-host body)
            status (:status resp)]
          (is (= 202 status))))

    (testing "update algorithm")
    (let [body     {:algorithm "good"
                    :enabled true
                    :inputs_url_template "http://anotherhost"}
          response (upsert-algorithm http-host body)
          status   (:status response)]
        (is (= 202 status)))

    (testing "get algorithm"
      (let [resp     (get-algorithm http-host "good")
            status   (:status resp)
            body     (:body resp)
            expected {:algorithm "good"
                      :enabled true
                      :inputs_url_template "http://anotherhost"}]
        (is (= 200 status))
        (is (= (json/decode body true) expected))))

    (testing "get algorithms"
      (let [resp   (get-algorithms http-host)
            status (:status resp)
            body   (:body resp)
            expected [{:algorithm "good"
                       :enabled true
                       :inputs_url_template "http://anotherhost"}]]
       (is (= 200 status))
       (is (= (json/decode body true) expected))))))

(deftest results
  (with-system
    ;; put a good alg in the system
    (upsert-algorithm http-host {:algorithm "test-alg"
                                 :enabled true
                                 :inputs_url_template
                                 "http://host/{algorithm}/{x}/{y}/{now}"})

    (testing "run non-existent algorithm")
    (testing "schedule existing algorithm")
    (testing "schedule same algorithm, get ticket")
    (testing "retrieve algorithm results once available")))
