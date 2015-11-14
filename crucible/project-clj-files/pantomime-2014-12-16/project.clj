(defproject com.novemberain/pantomime "2.4.0-SNAPSHOT"
  :min-lein-version "2.5.0"
  :description "A tiny Clojure library that deals with MIME types"
  :license { :name "Eclipse Public License" }
  :source-paths ["src/clojure"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.apache.tika/tika-core "1.5"]]
  :profiles {:dev {:resource-paths ["test/resources"]
                   :dependencies [[clj-http "0.9.1"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-master-SNAPSHOT"]]}
             :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :aliases  {"all" ["with-profile" "+dev:+1.5:+1.7:+master"]}
  :global-vars {*warn-on-reflection* true})
