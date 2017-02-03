(ns lcmap.clownfish.server-test
  "Full integration tests."
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            ;; have to require server to include server state
            ;; otherwise it will never be started
            [lcmap.clownfish.server :as server]
            [lcmap.clownfish.shared :refer [http-host with-system req]]))

(deftest changes-tests
  (with-system
    (testing "entry-point"
      (let [resp (req :get http-host)]
        (is (= 200 (:status resp)))))

    (testing "search for an unsupported type still returns JSON"
      (let [resp (req :get http-host
                      :headers {"Accept" "application/foo"})]
        (is (= 200 (:status resp)))
        (is (= "application/json" (get-in resp [:headers :content-type])))))))

(deftest changes-health-resource
  (with-system
    (testing "health check"
      (let [resp (req :get (str http-host "/health")
                      :headers {"Accept" "*/*"})]
        (is (= 200 (:status resp)))))))
