(defproject clojurewerkz/elastisch "2.2.0-SNAPSHOT"
  :url "http://clojureelasticsearch.info"
  :description "Minimalistic fully featured well documented Clojure ElasticSearch client"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure   "1.6.0"]
                 [cheshire              "5.4.0"]
                 [clj-http              "1.0.1" :exclusions [org.clojure/clojure]]
                 [clojurewerkz/support  "1.1.0"]
                 ;; used by the native client
                 [org.elasticsearch/elasticsearch "1.4.2"]]
  :min-lein-version "2.5.0"
  :profiles     {:dev {:resource-paths ["test/resources"]
                       :dependencies [[clj-time "0.8.0" :exclusions [org.clojure/clojure]]]
                       :plugins [[codox "0.8.10"]]
                       :codox {:sources ["src"]
                               :output-dir "doc/api"}}
                 ;; this version of clj-http depends on HTTPCore 4.2.x which
                 ;; some projects (e.g. using Spring's RestTemplate) can rely on,
                 ;; so we test for compatibility with it. MK.
                 :cljhttp076 {:dependencies [[clj-http "0.7.6"]]}
                 :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
                 :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
                 :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
                 :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  :aliases      {"all" ["with-profile" "dev:dev,1.5:dev,1.7:dev,cljhttp076:dev,1.5,cljhttp076"]}
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
                   :default     (fn [m] (not (:version-dependent m)))
                   :ci          (fn [m] (and (not (:native m)) (not (:version-dependent m))))}
  :mailing-list {:name "clojure-elasticsearch"
                 :archive "https://groups.google.com/group/clojure-elasticsearch"
                 :post "clojure-elasticsearch@googlegroups.com"})
