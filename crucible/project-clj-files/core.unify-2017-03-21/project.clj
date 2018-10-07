(defproject core.unify "0.5.6-SNAPSHOT"
  :description "Clojure unification library."
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :dev-dependencies [[jline "0.9.94"]
                     [lein-marginalia "0.7.1"]
                     [lein-multi "1.1.0"]]
  :multi-deps {"1.2"   [[org.clojure/clojure "1.2.0"]]
               "1.2.1" [[org.clojure/clojure "1.2.1"]]
               "1.3"   [[org.clojure/clojure "1.3.0"]]
               "1.4"   [[org.clojure/clojure "1.4.0"]]
               "1.5"   [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]]}
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :plugins [[lein-swank "1.4.4"]]
  :repositories {"sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}}
  :source-paths ["src/main/clojure"]
  :test-paths   ["src/test/clojure"])
