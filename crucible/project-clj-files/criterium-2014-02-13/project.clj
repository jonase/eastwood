(defproject criterium "0.4.4-SNAPSHOT"
  :description "Benchmarking library"
  :url "https://github.com/hugoduncan/criterium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:hugoduncan/criterium.git"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :local-repo-classpath true)
