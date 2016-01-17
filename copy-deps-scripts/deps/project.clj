(defproject deps "0.1.5-alpha1"
  :description "A project only for tracking dependencies of Eastwood"
  ;;:aot [eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo]
  ;; This project.clj file has the same dependencies as Eastwood's project.clj file used to, before I copied in the source of many of those dependencies.

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.analyzer "0.6.7"]
                 [org.clojure/tools.analyzer.jvm "0.6.9"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 [org.clojure/tools.reader "1.0.0-alpha3"]
                 [leinjacker "0.4.1"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.macro "0.1.2"]
                                  [jafingerhut/dolly "0.1.0"]]
                   ;; I want the namespaces defined in files in
                   ;; "cases" and its subdirectories to be accessible
                   ;; during 'lein test' in the classpath, but they
                   ;; themselves should not be eval'd by 'lein test'.
                   ;; I also want those namespaces available for
                   ;; one-off tests at the command line like "lein
                   ;; eastwood '{:namespaces [testcases.testtest]}'",
                   ;; and for that, the :test profile does not work,
                   ;; so put them in :dev
                   :resource-paths [ "cases" ]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  ;; Eastwood may work with earlier Leiningen versions, but this is
  ;; close to the earliest version that it was most tested with.
  :min-lein-version "2.3.0")
