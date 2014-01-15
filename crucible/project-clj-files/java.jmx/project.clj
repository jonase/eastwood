(defproject java.jmx "0.3.0-SNAPSHOT"
  :description "java.jmx 0.3.0-SNAPSHOT"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
