(defproject stencil "0.3.3"
  :description "Mustache in Clojure"
  :url "https://github.com/davidsantiago/stencil"
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [scout "0.1.0"]
                 [quoin "0.1.0"]
                 [slingshot "0.10.3"]
                 [org.clojure/core.cache "0.6.3"]]
  :profiles {:dev {:dependencies [[org.clojure/data.json "0.1.2"]]}
             :cacheless-test
             {:dependencies ^:replace [[org.clojure/clojure "1.3.0"]
                                       [scout "0.1.0"]
                                       [quoin "0.1.0"]
                                       [slingshot "0.10.3"]
                                       [org.clojure/data.json "0.1.2"]]}
             :clj1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :clj1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :clj1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :clj1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6    {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7    {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  :aliases {"all" ["with-profile" "dev:dev,clj1.4:dev,clj1.5"]
            "test-no-cache" ["with-profile" "+cacheless-test" "test"]}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :test-paths ["test/" "target/test/spec"])
