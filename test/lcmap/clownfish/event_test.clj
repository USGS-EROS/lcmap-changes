(ns lcmap.clownfish.event-test
  (:require [clojure.test :refer :all]
            [lcmap.clownfish.shared :as shared]
            [lcmap.clownfish.event :as event]))

(deftest testing-event-state
  (shared/with-system
    (testing "connection is open"
      (is (.isOpen event/amqp-connection)))
    (testing "channel is open"
      (is (.isOpen event/amqp-channel)))))
