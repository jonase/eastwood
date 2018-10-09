(defproject automat "0.2.4"
  :description ""
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[rhizome "0.2.7"]
                 [primitive-math "0.1.5"]
                 [potemkin "0.4.3"]
                 [proteus "0.1.6"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.9.473"]
                                  [org.clojure/test.check "0.9.0"]
                                  [criterium "0.4.4"]]
                   ;:prep-tasks ["compile" "javac"]
                   :auto-clean false
                   :aliases {"clean-test" ["do" "clean," "javac," "compile," "test," "cljsbuild" "test"]
                             "clean-build" ["do" "clean," "javac," "compile," "cljsbuild" "once"]}}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :test-selectors {:default #(every? (complement #{:stress :benchmark}) (keys %))
                   :stress :stress
                   :benchmark :benchmark}
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server" "-Xmx2g"]
  :java-source-paths ["src"]
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :jar-exclusions [#"\.DS_Store"]
  :source-paths ["src" "target/src" "target/classes"]
  :test-paths ["test" "target/test"]
  :plugins [[lein-codox "0.9.4"]
            [com.cemerick/clojurescript.test "0.3.3"]
            [lein-cljsbuild "1.1.2"]]
  :cljsbuild {:builds [{:source-paths ["src" "test"]
                        :compiler {:output-to "target/test.js"
                                   :source-map "target/test.js.map"
                                   :output-dir "target/js"
                                   :optimizations :advanced}}]
              :test-commands {"phantom" ["phantomjs" :runner "target/test.js"]}}
  :codox {:source-uri "https://github.com/ztellman/automat/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}
          :namespaces [automat.core automat.viz]})
