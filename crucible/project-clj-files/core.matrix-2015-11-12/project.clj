;; This project.clj is provided as a convenience for Leiningen users
;;
;; The official core.matrix project configuration is in the pom.xml
;; dependencies / configuration in this file may be out of date
;; if in doubt, please refer to the latest pom.xml

(defproject net.mikera/core.matrix "0.44.0"
  :url "https://github.com/mikera/core.matrix"
  :license {:name "Eclipse Public License (EPL)"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java" "src/test/java"]

  :test-paths ["src/test/clojure" "src/test/java"]

  :dependencies [[org.clojure/clojure "1.8.0-beta2"]]

  :marginalia {:javascript ["http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML"]}

  :profiles {:dev {:dependencies [[net.mikera/cljunit "0.3.1"]
                                  [criterium/criterium "0.4.3"]
                                  [org.clojure/tools.macro "0.1.5"]
                                  [hiccup "1.0.5"]
                                  [clatrix "0.5.0"]
                                  [net.mikera/vectorz-clj "0.37.0"]
                                  [org.clojure/test.check "0.8.2"]]                      
                   
                   :source-paths ["src/dev/clojure"]
                   :jvm-opts ^:replace []
                   :plugins [[lein-codox "0.9.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
            }
  
  :codox {:namespaces [clojure.core.matrix
                       clojure.core.matrix.dataset
                       clojure.core.matrix.io
                       clojure.core.matrix.linear
                       clojure.core.matrix.random
                       clojure.core.matrix.operators
                       clojure.core.matrix.protocols
                       clojure.core.matrix.random
                       clojure.core.matrix.implementations
                       clojure.core.matrix.select
                       clojure.core.matrix.stats]
          :src-dir-uri "https://github.com/mikera/core.matrix/blob/master/"
          :src-linenum-anchor-prefix "L"})
