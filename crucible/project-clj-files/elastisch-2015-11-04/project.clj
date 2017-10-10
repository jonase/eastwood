(defproject clojurewerkz/elastisch "2.2.0-rc1-SNAPSHOT"
  :url "http://clojureelasticsearch.info"
  :description "Minimalistic fully featured well documented Clojure ElasticSearch client"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure   "1.7.0"]
                 [cheshire              "5.5.0"]
                 [clj-http              "2.0.0" :exclusions [org.clojure/clojure]]
                 [clojurewerkz/support  "1.1.0" :exclusions [com.google.guava/guava]]
                 ;; used by the native client
                 [org.elasticsearch/elasticsearch "1.7.2"]]
  :min-lein-version "2.5.1"
  :profiles     {:dev {:resource-paths ["test/resources"]
                       :dependencies [[clj-time "0.9.0" :exclusions [org.clojure/clojure]]]
                       :plugins [[codox           "0.8.12"]
                                 [jonase/eastwood "0.2.1"]]
                       :codox {:sources ["src"]
                               :output-dir "doc/api"}}
                 ;; this version of clj-http depends on HTTPCore 4.2.x which
                 ;; some projects (e.g. using Spring's RestTemplate) can rely on,
                 ;; so we test for compatibility with it. MK.
                 :cljhttp076 {:dependencies [[clj-http "0.7.6"]]}
                 :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
                 :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
                 :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
                 :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
                 :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
                 :master {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}}
  :aliases      {"all" ["with-profile" "dev:dev,1.7:dev,cljhttp076"]}
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
                   :scroll      :scroll
                   :snapshots   :snapshots
                   :native      :native
                   :rest        :rest
                   :version-dependent :version-dependent
                   :all         (constantly true)
                   :default     (fn [m] (not (or (:version-dependent m)
                                                 (:scripting m))))
                   :ci          (fn [m] (and (not (:native m)) (not (:version-dependent m))))}
  :mailing-list {:name "clojure-elasticsearch"
                 :archive "https://groups.google.com/group/clojure-elasticsearch"
                 :post "clojure-elasticsearch@googlegroups.com"})
