(defproject clojurewerkz/neocons "2.1.0-beta1-SNAPSHOT"
  :description "Neocons is a feature rich idiomatic Clojure client for the Neo4J REST API"
  :url "http://clojureneo4j.info"
  :license {:name "Eclipse Public License"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure  "1.5.1"]
                 [cheshire             "5.2.0"]
                 [clj-http             "0.7.8"]
                 [clojurewerkz/support "0.20.0"]
                 [clojurewerkz/urly    "2.0.0-alpha5"]]
  :test-selectors {:default        (fn [m] (and (not (:time-consuming m))
                                                (not (:http-auth m))
                                                (not (:edge-features m))
                                                (not (:spatial m))))
                   :time-consuming :time-consuming
                   :focus          :focus
                   :indexing       :indexing
                   :cypher         :cypher
                   :http-auth      :http-auth
                   :spatial        :spatial
                   ;; as in, bleeding edge Neo4J Server
                   :edge-features  :edge-features
                   ;; assorted examples (extra integration tests)
                   :examples       :examples
                   :batching       :batching
                   :traversal      :traversal
                   :uri-encoding   (fn [m] (or (:examples m)
                                               (:indexing m)))
                   :all            (constantly true)}
  :source-paths ["src/clojure"]
  :profiles       {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
                   :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
                   :master {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
                   :dev {:plugins [[codox "0.6.6"]]
                         :codox {:sources ["src/clojure"]
                                 :output-dir "doc/api"}}}
  :codox {:src-dir-uri "https://github.com/michaelklishin/neocons/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :aliases        {"all" ["with-profile" "dev:dev,1.4:dev,1.6:dev,master"]}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :java-source-paths ["src/java"]
  :global-vars {*warn-on-reflection* true})
