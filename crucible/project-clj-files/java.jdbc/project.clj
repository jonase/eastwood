(defproject org.clojure/java.jdbc "0.3.0-SNAPSHOT"
  :description "A low-level Clojure wrapper for JDBC-based access to databases."
  :parent [org.clojure/pom.contrib "0.1.2"]
  :url "https://github.com/clojure/java.jdbc"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]
                 [mysql/mysql-connector-java "5.1.25"]
                 [org.apache.derby/derby "10.8.1.2"]
                 [hsqldb "1.8.0.10"]
                 [postgresql "8.4-702.jdbc4"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [net.sourceforge.jtds/jtds "1.2.4"]
                 ;; if you have the MS driver in your local repo
                 ;; [sqljdbc4 "3.0"]
                 ]
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :aliases {"test-all" ["with-profile" "test,1.2:test,1.3:test,1.4:test,1.5:test,1.6" "test"]
            "check-all" ["with-profile" "1.2:1.3:1.4:1.5:1.6" "check"]}
  :min-lein-version "2.0.0")
