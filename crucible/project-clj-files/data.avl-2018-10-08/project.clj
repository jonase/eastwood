(defproject org.clojure/data.avl "0.0.18-SNAPSHOT"
  :description "Persistent sorted maps and sets with log-time rank queries"
  :url "https://github.com/clojure/data.avl"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.6.1"
  :parent [org.clojure/pom.contrib "0.1.2"]
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :jvm-opts ^:replace ["-Dorg.clojure.data.avl.test.large-tree-size=100000"
                       "-Dorg.clojure.data.avl.test.medium-tree-size=100000"
                       "-Dorg.clojure.data.avl.test.small-tree-size=300"]
  :source-paths ["src/main/clojure" "src/main/cljs"]
  :test-paths ["src/test/clojure"]
  :aliases {"all" ["with-profile" "dev:dev,1.6:dev,1.7:dev,1.8:dev,1.9"]}
  :global-vars {*warn-on-reflection* true}
  :profiles {:dbg  {:jvm-opts ["-XX:-OmitStackTraceInFastThrow"]}
             :cljs {:dependencies [[org.clojure/clojure "1.9.0-beta2"]
                                   [org.clojure/clojurescript "1.9.946"]
                                   [org.clojure/test.check "0.9.0"]
                                   [collection-check "0.1.7"]]
                    :hooks [leiningen.cljsbuild]
                    :plugins [[lein-cljsbuild "1.1.4"]]
                    :cljsbuild
                    {:test-commands {"phantom" ["phantomjs" "out.test.js"]}
                     :builds {:test
                              {:source-paths ["src/main/cljs"
                                              "src/test/clojure"
                                              "src/test_local/cljc"
                                              "src/test/cljs"]
                               :compiler {:output-to "out/test.js"
                                          :main clojure.data.avl-test-runner
                                          :optimizations :advanced
                                          :pretty-print false
                                          :static-fns true}}}}}
             :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7  {:dependencies [[org.clojure/clojure "1.7.0"]
                                   [org.clojure/test.check "0.9.0"]
                                   [collection-check "0.1.7"]]
                    :test-paths ["src/test_local/cljc"]}
             :1.8  {:dependencies [[org.clojure/clojure "1.8.0"]
                                   [org.clojure/test.check "0.9.0"]
                                   [collection-check "0.1.7"]]
                    :test-paths ["src/test_local/cljc"]}
             :1.9  {:dependencies [[org.clojure/clojure "1.9.0"]
                                   [org.clojure/test.check "0.9.0"]
                                   [collection-check "0.1.7"]]
                    :test-paths ["src/test_local/cljc"]}
             :1.10  {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]
                                   [org.clojure/test.check "0.9.0"]
                                   [collection-check "0.1.7"]]
                    :test-paths ["src/test_local/cljc"]}})
