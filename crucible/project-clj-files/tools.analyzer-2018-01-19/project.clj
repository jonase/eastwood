(defproject org.clojure/tools.analyzer "0.6.8-SNAPSHOT"
  :description "An analyzer for Clojure code, written in Clojure and producing AST in EDN."
  :url "https://github.com/clojure/tools.analyzer"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.datomic/datomic-free "0.9.5327" :scope "provided" :exclusions [joda-time]]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :global-vars {*warn-on-reflection* true}
  :repositories [["sonatype" "https://oss.sonatype.org/content/repositories/releases"]
                 ["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]])
