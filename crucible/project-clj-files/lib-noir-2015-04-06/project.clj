(defproject lib-noir "0.9.8"
  :description "Libraries from Noir for your enjoyment."
  :url "https://github.com/noir-clojure/lib-noir"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-defaults "0.1.2"]
                 [cheshire "5.4.0"]
                 [ring "1.3.2"]
                 [compojure "1.3.3"]
                 [clout "2.1.1"]
                 [hiccup "1.0.5"]
                 [ring-middleware-format "0.5.0"]
                 [ring/ring-session-timeout "0.1.0"]
                 [clojurewerkz/scrypt "1.2.0"]]
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
             }
  :plugins [[codox "0.8.10"]
            [lein-ancient "0.5.5"]]
  :codox {:output-dir "doc"})
