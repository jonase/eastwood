(defproject prismatic/schema "0.3.3-SNAPSHOT"
  :description "Clojure(Script) library for declarative data description and validation"
  :url "http://github.com/prismatic/schema"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[potemkin "0.3.2"]]

  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [org.clojure/clojurescript "0.0-2120"]
                                  [com.keminglabs/cljx "0.3.1"]]
                   :plugins [[com.keminglabs/cljx "0.3.1"]
                             [codox "0.8.8"]
                             [lein-cljsbuild "1.0.2"]
                             [com.cemerick/clojurescript.test "0.3.1"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl
                                                     cljx.repl-middleware/wrap-cljx]}
                   :cljx {:builds [{:source-paths ["src/cljx"]
                                    :output-path "target/generated/src/clj"
                                    :rules :clj}
                                   {:source-paths ["src/cljx"]
                                    :output-path "target/generated/src/cljs"
                                    :rules :cljs}
                                   {:source-paths ["test/cljx"]
                                    :output-path "target/generated/test/clj"
                                    :rules :clj}
                                   {:source-paths ["test/cljx"]
                                    :output-path "target/generated/test/cljs"
                                    :rules :cljs}]}}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}

  :aliases {"all" ["with-profile" "dev:dev,1.5:dev,1.7"]
            "deploy" ["do" "clean," "cljx" "once," "deploy" "clojars"]
            "test" ["do" "clean," "cljx" "once," "test," "with-profile" "dev" "cljsbuild" "test"]}

  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]

  :lein-release {:deploy-via :shell
                 :shell ["lein" "deploy"]}

  :auto-clean false

  :source-paths ["target/generated/src/clj" "src/clj"]

  :resource-paths ["target/generated/src/cljs"]

  :test-paths ["target/generated/test/clj" "test/clj"]

  :cljsbuild {:test-commands {"unit" ["phantomjs" :runner
                                      "this.literal_js_was_evaluated=true"
                                      "target/unit-test.js"]
                              "unit-no-assert" ["phantomjs" :runner
                                                "this.literal_js_was_evaluated=true"
                                                "target/unit-test-no-assert.js"]}
              :builds
              {:dev {:source-paths ["src/clj" "target/generated/src/cljs"]
                     :compiler {:output-to "target/main.js"
                                :optimizations :whitespace
                                :pretty-print true}}
               :test {:source-paths ["src/clj" "test/clj"
                                     "target/generated/src/cljs"
                                     "target/generated/test/cljs"]
                      :compiler {:output-to "target/unit-test.js"
                                 :optimizations :whitespace
                                 :pretty-print true}}
               :test-no-assert {:source-paths ["src/clj" "test/clj"
                                               "target/generated/src/cljs"
                                               "target/generated/test/cljs"]
                                :assert false
                                :compiler {:output-to "target/unit-test-no-assert.js"
                                           :optimizations :whitespace
                                           :pretty-print true}}}}

  :codox {:src-uri-mapping {#"target/generated/src/clj" #(str "src/cljx/" % "x")}
          :src-dir-uri "http://github.com/prismatic/schema/blob/master/"
          :src-linenum-anchor-prefix "L"})