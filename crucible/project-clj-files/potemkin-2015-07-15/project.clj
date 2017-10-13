(defproject potemkin "0.4.1"
  :license {:name "MIT License"}
  :description "Some useful facades."
  :dependencies [[clj-tuple "0.2.2"]
                 [riddley "0.1.10"]]
  :profiles {:dev {:dependencies [[criterium "0.4.3"]
                                  [collection-check "0.1.6"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-master-SNAPSHOT"]]}
             :provided {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :global-vars {*warn-on-reflection* true}
  :test-selectors {:default #(not (some #{:benchmark}
                                        (cons (:tag %) (keys %))))
                   :benchmark :benchmark
                   :all (constantly true)}
  :java-source-paths ["src"]
  :jvm-opts ^:replace ["-server" "-Xmx4g"]
  :javac-options ["-source" "1.6" "-target" "1.6"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
