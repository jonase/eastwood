(defproject tools.nrepl "0.2.4-SNAPSHOT"
  :description "tools.nrepl 0.2.4-SNAPSHOT"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  )
