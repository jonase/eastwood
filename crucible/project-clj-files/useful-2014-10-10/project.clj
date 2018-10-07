(defproject org.flatland/useful "0.11.3"
  :description "A collection of generally-useful Clojure utility functions"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :url "https://github.com/flatland/useful"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.macro "0.1.1"]
                 [org.clojure/tools.reader "0.7.2"]]
  :aliases {"testall" ["with-profile" "dev,default:dev,1.3,default:dev,1.4,default:dev,1.5,default:dev,1.7,default" "test"]}
  :global-vars {*warn-on-reflection* true}
  :profiles {:1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]} }
  :plugins [[codox "0.8.0"]]
  :codox {:src-dir-uri "http://github.com/flatland/useful/blob/develop/"
          :src-linenum-anchor-prefix "L"})
