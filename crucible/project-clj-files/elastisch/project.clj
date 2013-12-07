(defproject clojurewerkz/elastisch "1.3.0-rc3-SNAPSHOT"
  :url "http://clojureelasticsearch.info"
  :description "Minimalistic fully featured well documented Clojure ElasticSearch client"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure   "1.5.1"]
                 [cheshire              "5.2.0"]
                 [clj-http              "0.7.7" :exclusions [org.clojure/clojure]]
                 [clojurewerkz/support  "0.20.0"]
                 ;; used by the native client
                 [org.elasticsearch/elasticsearch "0.90.7"]]
  :min-lein-version "2.0.0"
  :profiles     {:dev {:resource-paths ["test/resources"]
                       :dependencies [[clj-time            "0.4.4" :exclusions [org.clojure/clojure]]]
                       :plugins [[codox "0.6.1"]]}
                 :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
                 :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
                 :master {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :aliases      {"all" ["with-profile" "dev:dev,1.4:dev,1.6"]}
  :repositories {"sonatype"         {:url "http://oss.sonatype.org/content/repositories/releases"
                                     :snapshots false
                                     :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :global-vars {*warn-on-reflection* true}
  :test-selectors {:focus       :focus
                   :indexing    :indexing
                   :query       :query
                   :facets      :facets
                   :percolation :percolation
                   :native      :native
                   :all         (constantly true)
                   :default     (constantly true)
                   :ci          (fn [m] (not (:native m)))}
  :mailing-list {:name "clojure-elasticsearch"
                 :archive "https://groups.google.com/group/clojure-elasticsearch"
                 :post "clojure-elasticsearch@googlegroups.com"}
  :plugins [[codox "0.6.4"]]
  :codox {:sources ["src"]
          :output-dir "doc/api"})
