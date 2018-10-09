(defproject org.clojure/math.combinatorics "0.1.5-SNAPSHOT"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7  {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8  {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9  {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :aliases {"test-all" ["with-profile" "test,1.6:test,1.7:test,1.8:test,1.9:test,1.10" "test"]
            "check-all" ["with-profile" "1.6:1.7:1.8:1.9:1.10" "check"]}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
