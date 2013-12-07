(defproject clojurewerkz/romulan "0.1.0-SNAPSHOT"
  :description "LMAX Disruptor in Clojure embrace"
  :min-lein-version "2.0.0"
  :url "http://github.com/clojurewerkz/romulan"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure                "1.3.0"]
                 [com.googlecode.disruptor/disruptor "2.8"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}}
  :aliases { "all" ["with-profile" "dev:dev,1.4"] }
  :source-paths      ["src/clojure"]
  :java-source-paths ["src/java"]
  :repositories {"clojure-releases" "http://build.clojure.org/releases"
                 "sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}}
  :warn-on-reflection true)
