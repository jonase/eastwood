(defproject org.clojure/tools.emitter.jvm "0.0.1-SNAPSHOT"
  :description "A JVM bytecode generator for ASTs compatible with tools.analyzer(.jvm)."
  :url "https://github.com/clojure/tools.emitter.jvm"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.analyzer.jvm "0.0.1-SNAPSHOT"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.ow2.asm/asm-all "4.1"]]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  )
