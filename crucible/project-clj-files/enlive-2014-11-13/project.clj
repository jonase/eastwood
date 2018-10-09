(defproject enlive "1.1.5"
  :min-lein-version "2.0.0"
  :description "a HTML selector-based (à la CSS) templating and transformation system for Clojure"
  :url "https://github.com/cgrand/enlive/"
  :profiles     {:dev {:resource-paths ["test/resources"]}
                 :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
                 :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
                 :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
                 :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
                 :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
                 }
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.ccil.cowan.tagsoup/tagsoup "1.2.1"]
                 [org.jsoup/jsoup "1.7.2"]])
