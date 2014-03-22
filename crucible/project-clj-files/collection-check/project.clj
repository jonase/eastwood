(defproject collection-check "0.1.3"
  :description "fuzz testing for alternate data structures"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[reiddraper/simple-check "0.5.6"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}})
