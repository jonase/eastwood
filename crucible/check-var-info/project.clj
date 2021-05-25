(defproject check-var-info "0.1.0"
  :description "Project just to check var-info.edn file contents"
  :url "https://github.com/jonase/eastwood"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[jonase/eastwood "0.4.3"]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 ;; Latest as of May 25 2021
                 ;; algo.generic
                 ;; algo.graph
                 ;; cheshire
                 ;; clj-http
                 ;; compojure
                 [org.clojure/core.async "1.3.618"]
                 [org.clojure/core.cache "1.0.207"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/data.codec "0.1.1"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/data.json "2.3.1"]
                 [org.clojure/data.priority-map "1.0.0"]
                 ;; data.xml
                 ;; data.zip
                 ;; loom?
                 ;; instaparse
                 [org.clojure/java.jdbc "0.7.12"]
                 ;; math.combinatorics
                 [org.clojure/math.numeric-tower "0.0.4"]
                 ;; medley
                 ;; plumbing
                 ;; potemkin
                 ;; ring
                 ;; schema
                 ;; timbre
                 ;; tools.analyzer
                 ;; tools.analyzer.jvm
                 ;; tools.cli
                 ;; tools.logging
                 ;; tools.macro
                 ;; tools.namespace
                 ;; tools.nrepl
                 [org.clojure/tools.reader "1.3.4"]
                 [org.clojure/tools.trace "0.7.11"]
                 ;; useful
                 ])
