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

(deftest changes-health-resource
  (with-system
    (testing "health check"
      (let [resp (log/spy (req :get (str http-host "/health")
                               :headers {"Accept" "*/*"}))]
        (is (= 200 (:status resp)))))))

(deftest algorithms
  (with-system
    (testing "no algorithms defined"
      (let [resp (log/spy (req :get (str http-host "/algorithms")
                               :headers {"Accept" "application/json"}))
            body (json/decode (:body resp))]
        (is (= 200 (:status resp)))
        (is (= 0 (count body)))
        (is (coll? body))))

    (testing "add algorithm - bad input_url_template"
      (let [body    {:enabled true
                     :blah-inputs_url_template "http://host:port/context"}
            headers {"Accept" "application/json"
                     "Content-Type" "application/json"}
            resp (req :put (str http-host "/algorithm/bad-input-url")
                      :body (json/encode body)
                      :headers headers)]
        ()))

    (testing "add good algorithms"
      (let [resp (req :get (str http-host "/algorithms"))]
        ()))

    (testing "update algorithm"
      (let [resp (req :get (str http-host "/algorithms"))]
        ()))

    (testing "get algorithm"
      (let [resp (req :get (str http-host "/algorithm/test-alg-1"))]
        ()))

    (testing "get algorithms"
      (let [resp (req :get (str http-host "/algorithms"))]
        ()))))

(deftest results
  (with-system
    (testing "run non-existent algorithm")
    (testing "schedule existing algorithm")
    (testing "schedule same algorithm, get ticket")
    (testing "retrieve algorithm results once available")))
