(defproject potemkin "0.3.4"
  :license {:name "MIT License"}
  :description "Some useful facades."
  :dependencies [[clj-tuple "0.1.2"]
                 [riddley "0.1.6"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [criterium "0.4.1"]
                                  [collection-check "0.1.1-SNAPSHOT"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0-master-SNAPSHOT"]]}}
  :global-vars {*warn-on-reflection* true}
  :aliases {"all" ["with-profile" "dev:dev,1.6"]}
  :test-selectors {:default #(not (some #{:benchmark}
                                        (cons (:tag %) (keys %))))
                   :benchmark :benchmark
                   :all (constantly true)}
  :jvm-opts ^:replace ["-server"]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"})
