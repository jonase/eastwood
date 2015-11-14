(defproject org.clojure/data.json "0.5.2-SNAPSHOT"
  :description "Generative test runner."
  :url "https://github.com/clojure/test.generators"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :parent [org.clojure/pom.contrib "0.1.2"]
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/data.generators "0.1.2"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile" "test,1.4:test,1.5:test,1.6" "test"]
            "check-all" ["with-profile" "1.4:1.5:1.6" "check"]}
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :source-paths ["src/main/clojure"]
  :test-paths   ["src/examples/clojure"])
