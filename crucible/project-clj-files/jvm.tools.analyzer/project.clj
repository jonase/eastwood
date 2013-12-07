(defproject org.clojure/jvm.tools.analyzer "0.4.5-SNAPSHOT"
  :description "Interface to Clojure Analyzer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.trace "0.7.3"]
                 ;[org.clojure/clojurescript "0.0-1806"]
                 ; temporary deps for CLJS
                 [com.google.javascript/closure-compiler "v20130603"]
                 [org.clojure/google-closure-library "0.0-20130212-95c19e7f0f5f"]
                 [org.clojure/data.json "0.2.2"]
                 [org.mozilla/rhino "1.7R4"]
                 [org.clojure/tools.reader "0.7.5"]

                 ]

  :global-vars {*warn-on-reflection* true}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  
  ;:repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  :profiles {:dev {:repl-options {:port 64363}}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}

  :source-paths ["src/main/clojure"
                 "../clojurescript/src/clj" 
                 "../clojurescript/src/cljs"]
  :test-paths ["src/test/clojure"])
