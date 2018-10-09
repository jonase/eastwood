(def VERSION "0.19.0-SNAPSHOT")

(defproject cider/cider-nrepl VERSION
  :description "nREPL middlewares for CIDER"
  :url "https://github.com/clojure-emacs/cider-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git" :url "https://github.com/clojure-emacs/cider-nrepl"}
  :global-vars {*warn-on-reflection* true}

  :dependencies [[nrepl "0.4.5"]
                 ^:source-dep [cider/orchard "0.3.1"]
                 ^:source-dep [thunknyc/profile "0.5.2"]
                 ^:source-dep [mvxcvi/puget "1.0.2"]
                 ^:source-dep [fipp "0.6.12"]
                 ^:source-dep [compliment "0.3.6"]
                 ^:source-dep [cljs-tooling "0.3.0"]
                 ^:source-dep [cljfmt "0.5.7" :exclusions [org.clojure/clojurescript]]
                 ;; Not used directly in cider-nrepl, but needed because of tool.namespace
                 ;; and the way MrAnderson processes dependencies
                 ;; See https://github.com/clojure-emacs/cider/issues/2176 for details
                 ^:source-dep [org.clojure/java.classpath "0.2.3"]
                 ^:source-dep [org.clojure/tools.namespace "0.3.0-alpha4"]
                 ^:source-dep [org.clojure/tools.trace "0.7.9"]
                 ^:source-dep [org.clojure/tools.reader "1.1.3.1"]]
  :plugins [[thomasa/mranderson "0.4.8"]]
  :exclusions [org.clojure/clojure]

  :filespecs [{:type :bytes :path "cider/cider-nrepl/project.clj" :bytes ~(slurp "project.clj")}]

  :test-paths ["test/common"] ;; See `test-clj` and `test-cljs` profiles below.

  :test-selectors {:default (fn [test-meta]
                              (if-let [min-version (:min-clj-version test-meta)]
                                (>= (compare (clojure-version) min-version) 0)
                                true))}

  :aliases {"bump-version" ["change" "version" "leiningen.release/bump-version"]}

  :release-tasks [["vcs" "assert-committed"]
                  ["bump-version" "release"]
                  ["vcs" "commit" "Release %s"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["bump-version"]
                  ["vcs" "commit" "Begin %s"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]]}

             :dev {:repl-options {:nrepl-middleware [cider.nrepl/wrap-apropos
                                                     cider.nrepl/wrap-classpath
                                                     cider.nrepl/wrap-complete
                                                     cider.nrepl/wrap-debug
                                                     cider.nrepl/wrap-enlighten
                                                     cider.nrepl/wrap-format
                                                     cider.nrepl/wrap-info
                                                     cider.nrepl/wrap-inspect
                                                     cider.nrepl/wrap-macroexpand
                                                     cider.nrepl/wrap-ns
                                                     cider.nrepl/wrap-out
                                                     cider.nrepl/wrap-content-type
                                                     cider.nrepl/wrap-slurp
                                                     cider.nrepl/wrap-pprint
                                                     cider.nrepl/wrap-pprint-fn
                                                     cider.nrepl/wrap-profile
                                                     cider.nrepl/wrap-refresh
                                                     cider.nrepl/wrap-resource
                                                     cider.nrepl/wrap-spec
                                                     cider.nrepl/wrap-stacktrace
                                                     cider.nrepl/wrap-test
                                                     cider.nrepl/wrap-trace
                                                     cider.nrepl/wrap-tracker
                                                     cider.nrepl/wrap-undef
                                                     cider.nrepl/wrap-version]}
                   :dependencies [;; For developing the Leiningen plugin.
                                  [leiningen-core "2.8.1"]
                                  ;; For the boot tasks namespace
                                  [boot/base "2.7.2"]
                                  [boot/core "2.7.2"]]}

             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.8.51" :scope "provided"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.9.946" :scope "provided"]]
                   :test-paths ["test/spec"]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]
                                  [org.clojure/clojurescript "1.9.946" :scope "provided"]]
                   :test-paths ["test/spec"]}
             :master {:repositories [["snapshots" "https://oss.sonatype.org/content/repositories/snapshots"]]
                      :dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]
                                     [org.clojure/clojurescript "1.9.946" :scope "provided"]]}

             :test-clj {:source-paths ["test/src"]
                        :java-source-paths ["test/java"]
                        :resource-paths ["test/resources"]
                        :test-paths ["test/clj"]}
             :test-cljs {:test-paths ["test/cljs"]
                         :dependencies [[cider/piggieback "0.3.9"]]}

             :sysutils {:plugins [[lein-sysutils "0.2.0"]]}

             :cloverage {:plugins [[lein-cloverage "1.0.11-SNAPSHOT"]]}

             :cljfmt {:plugins [[lein-cljfmt "0.5.7"]]
                      :cljfmt {:indents {as-> [[:inner 0]]
                                         with-debug-bindings [[:inner 0]]
                                         merge-meta [[:inner 0]]
                                         try-if-let [[:block 1]]
                                         if-class [[:block 1]]}}}

             :eastwood {:plugins [[jonase/eastwood "0.2.5"]]
                        :eastwood {:config-files ["eastwood.clj"]}}})
