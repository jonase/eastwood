(defproject org.clojure/tools.cli "0.4.2-SNAPSHOT"
  :description "Command line arguments library."
  :parent [org.clojure/pom.contrib "0.1.2"]
  :url "https://github.com/clojure/tools.cli"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}

             ;; Local CLJS development; not in pom.xml
             :dev {:dependencies [[org.clojure/clojurescript "0.0-2080"]]
                   :plugins [[lein-cljsbuild "1.0.0"]
                             [com.birdseye-sw/lein-dalap "0.1.1"]
                             [com.cemerick/clojurescript.test "0.2.1"]]
                   :hooks [leiningen.dalap]
                   :cljsbuild {:builds [{:source-paths ["src/main/clojure/cljs"
                                                        "src/test/clojure/cljs"]
                                         :compiler {:output-to "target/cli_test.js"
                                                    :optimizations :whitespace
                                                    :pretty-print true}}]
                               :test-commands {"phantomjs" ["phantomjs"
                                                            :runner
                                                            "target/cli_test.js"]}}}}

  :aliases {"test-all" ["with-profile" "test,1.8:test,1.9:test,1.10" "test"]
            "check-all" ["with-profile" "1.8:1.9:1.10" "check"]}
  :min-lein-version "2.0.0")
