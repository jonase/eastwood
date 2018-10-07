(defproject org.clojure/data.zip "0.1.2-SNAPSHOT"
  :description "System for filtering trees, and XML trees in particular"
  :global-vars {*warn-on-reflection* true}
  :parent [org.clojure/pom.contrib "0.1.2"]

  :source-paths ["src/main/clojure" "src/main/clojurescript"]
  :test-paths ["src/test/clojure" "src/test/clojurescript"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3211"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }

  :plugins [[lein-cljsbuild "1.0.6"]]

  :clean-targets ^{:protect false} ["resources/tests.js" "resources/out"]

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src/main/clojurescript" "src/test/clojurescript"]
     :compiler {:optimizations :none
                :output-to "resources/tests.js"
                :output-dir "resources/out-dev"
                :source-map true
                :verbose true
                :compiler-stats true}}
    {:id "simp"
     :source-paths ["src/main/clojurescript" "src/test/clojurescript"]
     :compiler {:optimizations :simple
                :static-fns true
                :output-to "resources/tests.js"
                :output-dir "resources/out-simp"
                :verbose true
                :compiler-stats true}}
    {:id "adv"
     :source-paths ["src/main/clojurescript" "src/test/clojurescript"]
     :compiler {:optimizations :advanced
                :output-to "resources/tests.js"
                :output-dir "resources/out-adv"
                :verbose true
                :compiler-stats true}}]})
