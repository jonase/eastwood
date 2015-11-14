(defproject org.clojure/core.rrb-vector "0.0.12-SNAPSHOT"
  :description "RRB-Trees for Clojure(Script) -- see Bagwell & Rompf"
  :url "https://github.com/clojure/core.rrb-vector"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.2.0"
  :parent [org.clojure/pom.contrib "0.1.2"]
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :source-paths ["src/main/clojure"
                 "src/main/cljs"]
  :test-paths ["src/test/clojure"]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace []
  :profiles {:dev {:test-paths ["src/test_local/clojure"]
                   :dependencies [[org.clojure/clojurescript "0.0-2371"]
                                  [collection-check "0.1.3"]]
                   :plugins [[lein-cljsbuild "1.0.3"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}}
  :cljsbuild {:builds {:test {:source-paths ["src/main/cljs"
                                             "src/test/cljs"]
                              :compiler {:optimizations :advanced
                                         :output-to "out/test.js"}}}})
