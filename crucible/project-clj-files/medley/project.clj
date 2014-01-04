(defproject medley "0.1.3"
  :description "A lightweight library of useful pure functions"
  :url "https://github.com/weavejester/medley"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :plugins [[codox "0.6.6"]]
  :profiles
  {:dev {:dependencies [[criterium "0.4.2"]]}
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}})
