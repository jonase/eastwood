(defproject avl.clj "0.0.9"
  :description "Persistent sorted maps and sets with log-time rank queries"
  :url "https://github.com/michalmarczyk/avl.clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :jvm-opts ^:replace []
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]
  :profiles {:dev {:dependencies [[org.clojure/clojurescript "0.0-1889"]]
                   :plugins [[lein-cljsbuild "0.3.3"]]
                   :cljsbuild
                   {:builds {:test
                             {:source-paths ["src/cljs" "test/clj" "test/cljs"]
                              :compiler {:output-to "out/test.js"
                                         :optimizations :advanced
                                         :pretty-print false}}}}}})
