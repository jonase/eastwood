(defproject org.clojure/tools.emitter.jvm "0.0.1-SNAPSHOT"
  :description "A JVM bytecode generator for ASTs compatible with tools.analyzer(.jvm)."
  :url "https://github.com/clojure/tools.emitter.jvm"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :global-vars {*warn-on-reflection* true}
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.analyzer.jvm "0.1.0-beta13"]
                 [org.clojure/tools.reader "0.8.4"]
                 [org.ow2.asm/asm-all "4.2"]])
