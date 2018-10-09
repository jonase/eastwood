(defproject org.clojure/tools.analyzer.jvm "0.7.3-SNAPSHOT"
  :description "Additional jvm-specific passes for tools.analyzer."
  :url "https://github.com/clojure/tools.analyzer.jvm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/tools.reader "1.1.1"]
                 [org.clojure/tools.analyzer "0.6.9"]
                 [org.ow2.asm/asm "5.2"]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :repositories [["sonatype" "https://oss.sonatype.org/content/repositories/releases"]
                 ["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]])
