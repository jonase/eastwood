(defproject ubergraph "0.5.1"
  :description "Feature-loaded graph implementation"
  :url "https://github.com/engelberg/ubergraph"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.priority-map "0.0.10"]
                 [aysylu/loom "1.0.1"]
                 [dorothy "0.0.6"]
                 [potemkin "0.4.3"]]
  :profiles {:1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
             }
  :codox {:output-path "doc"
          :namespaces [ubergraph.core ubergraph.alg]
          :source-uri "https://github.com/Engelberg/ubergraph/tree/master/{filepath}#L{line}"}
  )
