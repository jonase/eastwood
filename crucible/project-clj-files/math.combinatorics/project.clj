(defproject math.combinatorics "0.0.8-SNAPSHOT"
  ;;:description "clojure.math.combinatorics 0.0.7-SNAPSHOT"
  ;;:main clojure.math.combinatorics
  :dependencies [
                 [org.clojure/clojure "1.5.1"]
                 ;;[org.clojure/clojure "1.6.0-master-SNAPSHOT"]
                ]
  :profiles {:1.2   {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.2.1 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3   {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4   {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5   {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6   {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile" "test,1.2.1:test,1.3:test,1.4:test,1.5:test,1.6" "test"]
            "check-all" ["with-profile" "1.2.1:1.3:1.4:1.5:1.6" "check"]}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
