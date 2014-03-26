(defproject lib-noir "0.8.1"
  :description "Libraries from Noir for your enjoyment."
  :url "https://github.com/noir-clojure/lib-noir"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.3.1"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [clout "1.1.0"]
                 [hiccup "1.0.4"]
                 [ring-middleware-format "0.3.2"]
                 [org.mindrot/jbcrypt "0.3m"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  :plugins [[codox "0.6.4"]]
  :codox {:output-dir "doc"})
