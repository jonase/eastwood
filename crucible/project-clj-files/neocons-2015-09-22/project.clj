(defproject clojurewerkz/neocons "3.2.0-SNAPSHOT"
  :description "Neocons is a feature rich idiomatic Clojure client for the Neo4J REST API"
  :url "http://clojureneo4j.info"
  :license {:name "Eclipse Public License"}
  :min-lein-version "2.5.1"
  :dependencies [[org.clojure/clojure  "1.7.0"]
                 [cheshire             "5.5.0"]
                 [clj-http             "2.0.0" :exclusions [org.clojure/clojure]]
                 [clojurewerkz/support "1.1.0" :exclusions [com.google.guava/guava]]]
  :test-selectors {:default        (fn [m] (and (not (:time-consuming m))
                                                (not (:http-auth m))
                                                (not (:edge-features m))
                                                (not (:graphene m))
                                                (not (:spatial m))))
                   :travis         (fn [m] (and (not (:time-consuming m))
                                                (not (:http-auth m))
                                                (not (:edge-features m))
                                                (not (:spatial m))))
                   :time-consuming :time-consuming
                   :focus          :focus
                   :indexing       :indexing
                   :cypher         :cypher
                   :graphene       :graphene
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
  :profiles       {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
                   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
                   :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
                   :1.8 {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}
                   :master {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}
                   :dev {:plugins [[codox "0.8.10"]]
                         :codox {:sources ["src/clojure"]
                                 :output-dir "doc/api"}}
                   ;; this version of clj-http depends on HTTPCore 4.2.x which
                   ;; some projects (e.g. using Spring's RestTemplate) can rely on,
                   ;; so we test for compatibility with it. MK.
                   :cljhttp076 {:dependencies [[clj-http "0.7.6"]]}}
  :codox {:src-dir-uri "https://github.com/michaelklishin/neocons/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :aliases        {"all" ["with-profile" "dev:dev,1.5:dev,1.6:dev,master:dev,cljhttp076:dev,1.6,cljhttp076"]}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :java-source-paths ["src/java"]
  :global-vars {*warn-on-reflection* true})
