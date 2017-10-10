(defproject org.clojure/core.match "0.3.0-alpha4-SNAPSHOT"
  :description "Optimized pattern matching for Clojure"

  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :test-paths ["src/test/clojure"]
  :source-paths ["src/main/clojure"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2496" :scope "provided"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
             }

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [cider/cider-nrepl "0.8.1"]]

  :cljsbuild
  {:builds
   [{:id "test"
     :source-paths ["src/test/cljs"]
     :compiler {:output-to "out/test.js"
                :static-fns true
                :optimizations :simple}}
    {:id "test-adv"
     :source-paths ["src/test/cljs"]
     :compiler {:output-to "out/test.js"
                :optimizations :advanced}}]})
