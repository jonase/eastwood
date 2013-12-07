(defproject tools.logging "0.2.7-SNAPSHOT"
  :description "tools.logging 0.2.7-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]
                 [log4j "1.2.16"]
                 [commons-logging "1.1.1"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
