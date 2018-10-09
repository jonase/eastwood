(defproject org.clojure/data.csv "0.1.5-SNAPSHOT"
  :description "data.csv 0.1.5-SNAPSHOT"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
