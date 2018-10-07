(defproject clojurewerkz/meltdown "2.0.0-SNAPSHOT"
  :description "Clojure interface to Reactor, an event-driven programming toolkit for the JVM"
  :url "https://github.com/clojurewerkz/meltdown"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.projectreactor/reactor-core "1.1.5.RELEASE"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             :master {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}
             :dev {:resource-paths ["test/resources"]
                   :dependencies   [[com.lmax/disruptor "3.3.0"]]
                   :plugins [[codox "0.8.10"]]
                   :codox {:sources ["src/clojure"]
                           :output-dir "doc/api"}}}
  :aliases {"all" ["with-profile" "dev:dev,1.4:dev,1.5:dev,1.7:dev,master"]}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail}}
                 "springsource-milestone" {:url "http://repo.springsource.org/libs-milestone"
                                           :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :javac-options      ["-target" "1.6" "-source" "1.6"]
  :jvm-opts           ["-Dfile.encoding=utf-8"]
  :source-paths       ["src/clojure"]
  :java-source-paths  ["src/java"]
  :global-vars {*warn-on-reflection* true}
  :test-selectors     {:default     (fn [m] (not (:performance m)))
                       :performance :performance
                       :focus       :focus
                       :all         (constantly true)})
