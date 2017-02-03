(defproject lcmap-changes "0.1.0-SNAPSHOT"
  :description "Change detection HTTP resource for LCMAP"
  :url "http://github.com/usgs-eros/lcmap-changes"
  :license {:name "Unlicense"
            :url ""}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; http server
                 [ring/ring "1.5.1"]
                 [ring/ring-json "0.4.0"]
                 [usgs-eros/ring-accept "0.2.0-SNAPSHOT"]
                 [compojure "1.5.2"]
                 ;; json/xml/html
                 [cheshire "5.6.3"]
                 [org.clojure/data.xml "0.1.0-beta2"]
                 [enlive "1.1.6"]
                 [hiccup "1.0.5"]
                 ;; persistence
                 [com.datastax.cassandra/cassandra-driver-core "3.1.3"]
                 [cc.qbits/alia-all "3.3.0"]
                 [cc.qbits/hayt "3.2.0"]
                 ;; messaging
                 [com.novemberain/langohr "3.7.0"]
                 ;; logging
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.22"]
                 ;; state management
                 [mount "0.1.11"]
                 ;; configuration
                 [usgs-eros/uberconf "0.1.0-SNAPSHOT"]
                 ;; cryptographic checking
                 [digest "1.4.5"]
                 [dire "0.5.4"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/data.xml "0.1.0-beta1"]
                 [org.clojure/data.zip "0.1.2"]
                 [gov.usgs.eros/lcmap-commons "1.0.1-SNAPSHOT"]
                 ;; needed to make indexing calls to elasticsearch
                 [http-kit "2.2.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 ;; health check support
                 [metrics-clojure-ring "2.8.0"]
                 [metrics-clojure-jvm "2.8.0"]
                 [metrics-clojure-health "2.8.0"]
                 [listora/again "0.1.0"]]

  :profiles {:dev {:resource-paths ["dev" "dev/resources" "resources" "data"]
                   :dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]
                                  [http-kit "2.2.0"]
                                  [http-kit.fake "0.2.2"]
                                  [proto-repl "0.3.1"]]
                   :plugins [[lein-codox "0.10.0"]
                             [lein-ancient "0.6.10"]
                             [lein-kibit "0.1.2"]
                             [jonase/eastwood "0.2.3"]
                             [lein-cljfmt "0.5.6"]]}

             :test {:resource-paths ["test" "test/resources" "resources" "data"]
                    :dependencies [[http-kit.fake "0.2.2"]]}
             :repl {:resource-paths ["dev" "dev/resources" "resources" "data"]}
             :uberjar {:aot :all
                       :main lcmap.clownfish.core}}
  :main lcmap.clownfish.core
  :target-path "target/%s/"
  :compile-path "%s/classes"
  :repl-options {:init-ns user})
