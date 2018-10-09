(defproject org.clojure/tools.logging "0.3.2-SNAPSHOT"
  :description "Clojure logging API."
  :url "https://github.com/clojure/tools.logging"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :source-paths [ "src/main/clojure" ]
  :test-paths [ "src/test/clojure" ]
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:test {:dependencies [[org.slf4j/slf4j-api "1.6.2"]
                                   [org.slf4j/slf4j-log4j12 "1.6.2"]
                                   [log4j "1.2.16"]
                                   [commons-logging "1.1.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             })
