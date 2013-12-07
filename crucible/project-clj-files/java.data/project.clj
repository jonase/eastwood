(defproject algo.generic "0.1.2-SNAPSHOT"
  :description "algo.generic 0.1.2-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  :java-source-paths [ "src/test/java" ]
  )
