(ns lcmap.clownfish.db-test
  (:require [clojure.test :refer :all]
            [lcmap.clownfish.shared :as shared]
            [lcmap.clownfish.db :as db]))

(deftest testing-event-state
  (shared/with-system
    (testing "cluster connection is open"
      (is (not (.isClosed db/db-cluster))))
    (testing "session is open"
      (is (not (.isClosed db/db-session))))))
