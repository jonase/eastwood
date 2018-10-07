(defproject collection-check "0.1.7"
  :description "fuzz testing for alternate data structures"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/test.check "0.8.0"]
                 [com.gfredericks/test.chuck "0.1.21"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             })
