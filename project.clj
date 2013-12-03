(defproject jonase/eastwood "0.0.3"
  :description "A Clojure lint tool"
  :dependencies [[org.clojure/tools.analyzer "0.0.1-SNAPSHOT"]
                 [org.clojure/tools.analyzer.jvm "0.0.1-SNAPSHOT"]
                 [org.clojure/tools.namespace "0.1.2"]
                 [org.clojure/tools.reader "0.8.0"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]
                 [leinjacker "0.4.1"]]
  :eval-in-leiningen true
  :resource-paths ["resource"])
