(defproject org.clojure/core.memoize "0.7.2-SNAPSHOT"
  :description "A memoization library for Clojure."
  :dependencies [;;[org.clojure/pom.contrib "0.1.2"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.cache "0.7.1"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :plugins [[lein-swank "1.4.4"]
            [lein-marginalia "0.7.1"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :source-paths ["src/main/clojure"]
  :test-paths   ["src/test/clojure"]
  :global-vars {*warn-on-reflection* true})

