(defproject honeysql "0.9.0-beta1"
  :description "SQL as Clojure data structures"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/jkk/honeysql"
  :scm {:name "git"
        :url "https://github.com/jkk/honeysql"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :cljsbuild {:builds {:release {:source-paths ["src"]
                                 :compiler {:output-to "dist/honeysql.js"
                                            :optimizations :advanced
                                            :output-wrapper false
                                            :parallel-build true
                                            :pretty-print false}}
                       :test {:source-paths ["src" "test"]
                              :compiler {:output-to "target/test/honeysql.js"
                                         :output-dir "target/test"
                                         :source-map true
                                         :main honeysql.test
                                         :parallel-build true
                                         :target :nodejs}}}}
  :doo {:build "test"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.9.521"]
                                  [cljsbuild "1.1.6"]]
                   :plugins [[lein-cljsbuild "1.1.6"]
                             [lein-doo "0.1.6"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}})
