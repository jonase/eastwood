(defproject clojurewerkz/mailer "1.3.0-SNAPSHOT"
  :description "An ActionMailer-inspired mailer library. Combines Postal, Clostache, some conventions and support for multiple delivery modes"
  :url "https://github.com/clojurewerkz/mailer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure             "1.6.0"]
                 [com.draines/postal              "1.11.1"]
                 [de.ubercode.clostache/clostache "1.3.1"]
                 [clojurewerkz/support            "1.0.0"]
                 [clojurewerkz/route-one          "1.1.0"]]
  :test-selectors {:focus          :focus
                   :all            (constantly true)}
  :source-paths ["src/clojure"]
  :profiles {:dev {:resource-paths ["test/resources"]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
             }
  :aliases { "all" ["with-profile" "dev:dev,1.4:dev,1.5:dev,1.7:dev,master"] }
  :repositories {"clojure-releases" "http://build.clojure.org/releases"
                 "sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                             :releases {:checksum :fail :update :always}}}
  :global-vars {*warn-on-reflection* true})
