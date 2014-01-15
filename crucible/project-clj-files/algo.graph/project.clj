(defproject algo.graph "0.1.0-SNAPSHOT"
  :description "Basic graph theory algorithms."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}

  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}

  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"])
