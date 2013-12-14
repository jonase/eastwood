(defproject lib-noir "0.7.7"
  :description "Libraries from Noir for your enjoyment."
  :url "https://github.com/noir-clojure/lib-noir"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cheshire "5.2.0"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [clout "1.1.0"]
                 [hiccup "1.0.4"]
                 [ring-middleware-format "0.3.1"]
                 [org.mindrot/jbcrypt "0.3m"]]
  :plugins [[codox "0.6.4"]]
  :codox {:output-dir "doc"})
