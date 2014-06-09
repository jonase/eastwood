(defproject deps "0.1.2"
  :description "A project only for tracking dependencies of Eastwood"
  ;; This project.clj file has the same dependencies as Eastwood's project.clj file used to, before I copied in the source of many of those dependencies.

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.analyzer "0.2.1"]
                 [org.clojure/tools.analyzer.jvm "0.2.1"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/tools.reader "0.8.4"]
                 [leinjacker "0.4.1"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.macro "0.1.2"]]
                   :source-paths [ "cases" ]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  ;; Eastwood may work with earlier Leiningen versions, but this is
  ;; close to the earliest version that it was most tested with.
  :min-lein-version "2.3.0")
