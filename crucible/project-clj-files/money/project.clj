(defproject clojurewerkz/money "1.5.0-SNAPSHOT"
  :description "A Clojure library that deals with monetary values and currencies. Built on top of Joda Money."
  :min-lein-version "2.0.0"
  :url "http://github.com/clojurewerkz/money"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.joda/joda-money "0.8"]]
  :profiles {:dev {:plugins      [[codox "0.6.4"]]
                   :dependencies [[com.novemberain/monger "1.5.0"]
                                  [cheshire               "5.1.1"]
                                  [hiccup                 "1.0.3"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}
             :master {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :aliases  {"all" ["with-profile" "+dev:+1.4:+1.6:+master"]}
  :source-paths      ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths    ["src/resources"]
  :warn-on-reflection true)
