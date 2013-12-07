(defproject core.memoize "0.5.7-SNAPSHOT"
  :description "A memoization library for Clojure."
  :dependencies [;;[org.clojure/pom.contrib "0.1.2"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.cache "0.6.3"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :plugins [[lein-swank "1.4.4"]
            [lein-marginalia "0.7.1"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :source-paths ["src/main/clojure"]
  :test-paths   ["src/test/clojure"])

