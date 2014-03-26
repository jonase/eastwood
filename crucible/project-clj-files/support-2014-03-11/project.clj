(defproject clojurewerkz/support "0.21.0-SNAPSHOT"
  :description "A support library ClojureWerkz projects (Langohr, Monger, Welle, Neocons, Elastisch, Quartzite, etc) can rely on"
  :min-lein-version "2.0.0"
  :url "http://github.com/clojurewerkz/support"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.google.guava/guava "16.0"]]
  :profiles {:dev {:resource-paths ["test/resources"]
                   :dependencies   [[clj-time              "0.6.0"]
                                    [cheshire              "5.3.0"]]}
             :1.4    {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.6    {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7    {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
             :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
             :cdj01x {:dependencies [[org.clojure/data.json "0.1.2"]]}
             :cdj02x {:dependencies [[org.clojure/data.json "0.2.4"]]}}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                             :snapshots true
                             :releases {:checksum :fail :update :always}}}
  :aliases {"all" ["with-profile" "dev:dev,1.4:dev,1.6:dev,master:dev,cdj01x:dev,cdj02x"]}
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :global-vars {*warn-on-reflection* true})
