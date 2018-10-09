(defproject org.clojure/data.codec "0.1.2-SNAPSHOT"
  :description "Clojure codec implementations."
  :url "https://github.com/clojure/data.codec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :aliases {"perf" ["with-profile" "perf" "run"]}
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [commons-codec "1.5"]]
                   :plugins [[lein-cloverage "1.0.9"]]}
             :perf {:dependencies [[commons-codec "1.5"]
                                   [criterium "0.4.3"]]
                    :source-paths ["src/perf/clojure"]
                    :main clojure.data.codec.perf-base64}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             })
