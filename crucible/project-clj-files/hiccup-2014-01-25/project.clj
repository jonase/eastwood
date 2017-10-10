(defproject hiccup "1.0.5"
  :description "A fast library for rendering HTML in Clojure"
  :url "http://github.com/weavejester/hiccup"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :plugins [[codox "0.6.6"]]
  :codox {:exclude [hiccup.compiler]
          :src-dir-uri "http://github.com/weavejester/hiccup/blob/1.0.5/"
          :src-linenum-anchor-prefix "L"}
  :profiles
  {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
   :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
   })
