(defproject clojurewerkz/ogre "2.4.0.0-SNAPSHOT"
  :description "Clojure wrapper around gremlin"
  :url "https://github.com/clojurewerkz/ogre"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}  
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [potemkin "0.2.0"]
                 [com.tinkerpop.gremlin/gremlin-java "2.4.0"]
                 [com.tinkerpop.blueprints/blueprints-core "2.4.0"]
                 [com.tinkerpop/pipes "2.4.0"]]
  :test-paths ["test" "src/test/ogre"]
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
  :global-vars {*warn-on-reflection* true
                *assert* false})
