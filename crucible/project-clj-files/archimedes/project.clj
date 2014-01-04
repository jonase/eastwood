(defproject clojurewerkz/archimedes "1.0.0-alpha6-SNAPSHOT"
  :description "Clojure wrapper for Tinkerpop Blueprints"
  :url "https://github.com/clojurewerkz/archimedes"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [potemkin "0.2.0"]                 
                 [com.tinkerpop.blueprints/blueprints-core "2.4.0"]]
  :profiles {:1.4    {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.6    {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :master {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.4:dev,1.6"]}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  ;;  :warn-on-reflection true
  )
