(defproject medley "1.0.0"
  :description "A lightweight library of useful, mostly pure functions"
  :url "https://github.com/weavejester/medley"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :plugins [[lein-codox "0.10.3"]
            [lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]]
  :codox
  {:output-path "codox"
   :metadata {:doc/format :markdown}
   :source-uri "http://github.com/weavejester/medley/blob/{version}/{filepath}#L{line}"}
  :cljsbuild
  {:builds
   {:test
    {:source-paths ["src" "test"]
     :compiler {:output-to "target/main.js"
                :output-dir "target"
                :main medley.test-runner
                :optimizations :simple}}}}
  :doo {:paths {:rhino "lein run -m org.mozilla.javascript.tools.shell.Main"}}
  :aliases
  {"test-cljs" ["doo" "rhino" "test" "once"]
   "test-clj"  ["with-profile" "default:+1.8:+1.9" "test"]
   "test-all"  ["do" ["test-clj"] ["test-cljs"]]}
  :profiles
  {:provided {:dependencies [[org.clojure/clojurescript "1.7.228"]]}
   :test {:dependencies [[org.mozilla/rhino "1.7.7"]]}
   :dev {:dependencies [[criterium "0.4.3"]]
         :jvm-opts ^:replace {}}
   :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
  })
