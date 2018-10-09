(defproject clj-time/clj-time "0.14.4"
  :description "A date and time library for Clojure, wrapping Joda Time."
  :url "https://github.com/clj-time/clj-time"
  :mailing-list {:name "clj-time mailing list"
                 :archive "https://groups.google.com/forum/?fromgroups#!forum/clj-time"
                 :post "clj-time@googlegroups.com"}
  :license {:name "MIT License"
            :url "http://www.opensource.org/licenses/mit-license.php"
            :distribution :repo}
  :dependencies [[joda-time "2.9.9"]
                 [org.clojure/clojure "1.9.0" :scope "provided"]]
  :min-lein-version "2.0.0"
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/java.jdbc "0.7.5"]]
                   :plugins [[codox "0.8.10"]]}
             :midje {:dependencies [[midje "1.9.0-alpha5"]]
                     :plugins      [[lein-midje "3.2.1"]
                                    [midje-readme "1.0.9"]]
                     :midje-readme {:require "[clj-time.core :as t] [clj-time.predicates :as pr] [clj-time.format :as f] [clj-time.coerce :as c]"}}
             :1.7    {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8    {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9    {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10   {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             :master {:repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots/"]]
                      :dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             :spec   {:dependencies [[org.clojure/clojure "1.9.0"]
                                     [org.clojure/test.check "0.9.0"]]
                      :test-paths ["test" "test_clj_1.9"]}}

  :aliases {"test-all" ["with-profile" "dev,spec,default,midje:dev,master,default,midje:dev,default,midje:dev,1.7,midje:dev,1.8,midje" "test"]})
