(defproject jonase/eastwood "0.0.3"
  :description "A Clojure lint tool"
  :dependencies [;;[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojure "1.6.0-alpha3"]
                 [org.clojure/tools.analyzer "0.0.1-SNAPSHOT"]
                 [org.clojure/tools.analyzer.jvm "0.0.1-SNAPSHOT"]
                 [org.clojure/tools.namespace "0.1.2"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.clojure/tools.macro "0.1.2"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]
                 [leinjacker "0.4.1"]]
  ;; Note: comment out the following line if you want to do 'lein
  ;; test' and get a Clojure version later than 1.5.1, even if you
  ;; have an explicit org.clojure/clojure in the :dependencies
  :eval-in-leiningen true
  :resource-paths ["resource"])
