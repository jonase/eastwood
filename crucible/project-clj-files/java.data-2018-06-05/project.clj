(defproject org.clojure/java.data "0.1.2-SNAPSHOT"
  :description "java.data 0.1.2-SNAPSHOT"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  :java-source-paths [ "src/test/java" ]
  )
