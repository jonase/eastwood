(defproject org.clojure/tools.reader "1.0.0-SNAPSHOT"
  :description "A Clojure reader in Clojure"
  :parent [org.clojure/pom.contrib "0.2.0"]
  :url "https://github.com/clojure/tools.reader"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure" "src/main/cljs"]
  :test-paths ["src/test/clojure"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]
                 [org.clojure/clojurescript "1.9.494" :scope "provided"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile" "test,1.5:test,1.6:test,1.7:test,1.8:test,1.9" "test"]
            "check-all" ["with-profile" "1.5:1.6:1.7:1.8:1.9" "check"]}
  :min-lein-version "2.0.0"
  :global-vars {*warn-on-reflection* true
                *unchecked-math* :warn-on-boxed}
  :plugins [[lein-cljsbuild "1.1.4"]]
  :cljsbuild
  {:builds [{:id "dev"
             :source-paths ["src/main/cljs"]
             :compiler {:output-to "out/main.js"
                        :output-dir "out"
                        :optimizations :simple
                        :pretty-print true}}
            {:id "whitespace"
             :source-paths ["src/main/cljs" "src/test/cljs"]
             :compiler {:output-to "target/test/tests-whitespace.js"
                        :output-dir "target/test/out-whitespace"
                        :optimizations :whitespace}}
            {:id "simple"
             :source-paths ["src/main/cljs" "src/test/cljs"]
             :notify-command ["node" "target/test/tests-simple.js"]
             :compiler {:optimizations :simple
                        :output-to "target/test/tests-simple.js"
                        :output-dir "target/test/out-simple"}}
            {:id "advanced"
             :source-paths ["src/main/cljs" "src/test/cljs"]
             :compiler {:optimizations :advanced
                        :output-to "target/test/tests-advanced.js"
                        :output-dir "target/test/out-advanced"}}]
   :test-commands
   {"simple" ["node" "target/test/tests-simple.js"]
    "advanced" ["node" "target/test/tests-advanced.js"]}})
