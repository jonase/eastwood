(defproject net.mikera/core.matrix "0.62.1-SNAPSHOT"
  :url "https://github.com/mikera/core.matrix"
  :license {:name "Eclipse Public License (EPL)"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true}

  :dependencies [[org.clojure/clojure "1.8.0"]]

  :source-paths      ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths        [ "src/test/java" "src/test/clojure"]

  :profiles {:dev
             {:dependencies
              [[net.mikera/vectorz-clj "0.43.1" :exclusions [net.mikera/core.matrix]]
               [clatrix "0.5.0" :exclusions [net.mikera/core.matrix]]
               [org.clojure/test.check "0.9.0"]
               [net.mikera/cljunit "0.4.0"]
               [criterium/criterium "0.4.3"]
               [org.clojure/tools.macro "0.1.5"]
               ]


              :plugins [[lein-codox "0.9.0"]]
              :source-paths ["src/main/clojure" "src/test/cljs" "src/test/clojure"]
              :java-source-paths  ["src/test/java"]
              :jvm-opts ^:replace []}

             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}

             :clean-targets ^{:protect false} ["resources/public/js" :target]

             :test
             {:dependencies [[net.mikera/vectorz-clj "0.43.0" :exclusions [net.mikera/core.matrix]]
                             [clatrix "0.5.0" :exclusions [net.mikera/core.matrix]]
                             [net.mikera/cljunit "0.4.0"]
                             [criterium/criterium "0.4.3"]
                             [org.clojure/clojurescript "1.9.908"]
                             [org.clojure/tools.macro "0.1.5"]
                             [org.clojure/test.check "0.9.0"]]}

             :cljs
             {:dependencies [[org.clojure/clojurescript "1.9.908"]
                             [thinktopic/aljabr "0.1.0-SNAPSHOT" :exclusions [net.mikera/core.matrix]]
                             [figwheel-sidecar "0.5.8"]
                             [com.cemerick/piggieback "0.2.1"]
                             ]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
              :plugins [[lein-figwheel "0.5.13"]
                        [lein-doo "0.1.7"]
                        [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
                        ]

              :cljsbuild {:builds
                          {:dev {:figwheel true
                                 :source-paths ["src/main/clojure" "src/test/cljs" "src/test/clojure"]
                                 :compiler {:output-to "resources/public/js/core.matrix.js"
                                            :output-dir "resources/public/js/out"
                                            :asset-path   "out/"
                                            :optimizations :none
                                            :verbose true
                                            :warnings true
                                            :parallel-build true
                                            :pretty-print true}}
                           :test {:source-paths ["src/main/clojure" "src/test/cljs" "src/test/clojure"]
                                  :compiler {:output-to "resources/public/js/unit-test.js"
                                             :asset-path   "out/"
                                             :main clojure.core.matrix.cljs-runner
                                             :optimizations :advanced 
                                             :parallel-build true
                                             :verbose true
                                             :warnings true
                                             ;;avoids "typeerror: undefined is not an object " error
                                             ;; see https://github.com/bensu/doo/pull/141#issuecomment-318999398
                                             :process-shim false
                                             :pretty-print true}}}

                                        :figwheel {:load-warninged-code true :css-dirs ["resources/public/css"] :server-port 8765}
                          }
             }}

  :marginalia {:javascript ["https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML"]}

  ;;runs both clj and cljs tests on invocation of "lein test"
  :aliases {"test" ["do" "clean," "test," "with-profile" "cljs" "doo" "nashorn" "test" "once"]}
  :codox {:namespaces [clojure.core.matrix
                       clojure.core.matrix.dataset
                       clojure.core.matrix.io
                       clojure.core.matrix.linear
                       clojure.core.matrix.random
                       clojure.core.matrix.operators
                       clojure.core.matrix.protocols
                       clojure.core.matrix.random
                       clojure.core.matrix.implementations
                       clojure.core.matrix.selection
                       clojure.core.matrix.stats
                       clojure.core.matrix.utils]
          :src-dir-uri "https://github.com/mikera/core.matrix/blob/master/"
          :src-linenum-anchor-prefix "L"})

