(def eval-in-leiningen?
  (#{"1" "true"} (System/getenv "EVAL_IN_LEININGEN")))

(def plugin-source-path "lein-eastwood")

(defproject jonase/eastwood "0.3.14"
  :description "A Clojure lint tool"
  :url "https://github.com/jonase/eastwood"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ~(cond-> ["src" "copied-deps"]
                   eval-in-leiningen? (conj plugin-source-path))
  :dependencies [[org.clojure/clojure "1.10.2" :scope "provided"]
                 [org.clojars.brenton/google-diff-match-patch "0.1"]
                 [org.ow2.asm/asm-all "5.2"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.macro "0.1.5"]
                                  [jafingerhut/dolly "0.1.0"]]}
             :eastwood-plugin {:source-paths [~plugin-source-path]}
             :warn-on-reflection {:global-vars {*warn-on-reflection* true}}
             :test {:dependencies [[commons-io "2.4" #_"Needed for issue-173-test"]]
                    :resource-paths ["test-resources"
                                     ;; if wanting the `cases` to be available during development / the default profile,
                                     ;; please simply add `with-profile +test` to your CLI invocation.
                                     "cases"]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10.1 {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :1.10.2 {:dependencies [[org.clojure/clojure "1.10.2"]]}}
  :aliases {"test-all" ["with-profile"
                        ~(->> ["1.7" "1.8" "1.9" "1.10"]
                              (map (partial str "-user,-dev,+test,+warn-on-reflection,+"))
                              (clojure.string/join ":"))
                        "test"]}
  :eastwood {:source-paths ["src"]
             :test-paths ["test"]
             :debug #{}}
  :plugins [[net.assum/lein-ver "1.2.0"]]
  :lein-ver {:version-file "resources/EASTWOOD_VERSION"}
  ;; Eastwood may work with earlier Leiningen versions, but this is
  ;; close to the earliest version that it was most tested with.
  :min-lein-version "2.3.0"
  :resource-paths ["resource" "resources"]
  :eval-in ~(if eval-in-leiningen?
              :leiningen
              :subprocess))
