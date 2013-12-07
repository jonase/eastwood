(defproject tools.macro "0.1.6-SNAPSHOT"
  :description "tools.macro 0.1.6-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
