(defproject org.clojure/tools.reader "0.8.4-SNAPSHOT"
  :description "A Clojure reader in Clojure"
  :parent [org.clojure/pom.contrib "0.1.2"]
  :url "https://github.com/clojure/tools.reader"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :aot [clojure.tools.reader.impl.ExceptionInfo]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile" "test,1.3:test,1.4:test,1.5:test,1.6" "test"]
            "check-all" ["with-profile" "1.3:1.4:1.5:1.6" "check"]}
  :min-lein-version "2.0.0")
