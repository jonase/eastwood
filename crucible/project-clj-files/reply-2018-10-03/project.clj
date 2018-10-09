(let [dev-deps '[[speclj "2.7.2"]
                 [classlojure "0.6.6"]]]

  (defproject reply "0.4.2-SNAPSHOT"
    :description "REPL-y: A fitter, happier, more productive REPL for Clojure."
    :global-vars {*warn-on-reflection* true}
    :dependencies [[org.clojure/clojure "1.6.0"]
                   [jline "2.14.6"]
                   [org.thnetos/cd-client "0.3.6"]
                   [clj-stacktrace "0.2.7"]
                   [nrepl "0.4.2"]
                   [org.clojure/tools.cli "0.3.1"]
                   [nrepl/drawbridge "0.1.0"]
                   [trptcolin/versioneer "0.1.1"]
                   [clojure-complete "0.2.5"]
                   [org.clojars.trptcolin/sjacket "0.1.1.1"
                    :exclusions [org.clojure/clojure]]]
    :min-lein-version "2.0.0"
    :license {:name "Eclipse Public License"
              :url "http://www.eclipse.org/legal/epl-v10.html"}
    :url "https://github.com/trptcolin/reply"
    :profiles {:dev {:dependencies ~dev-deps}
               :base {:dependencies []}
               :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
               :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
               :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
               :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
               :1.10 {:dependencies [[org.clojure/clojure "1.10.0-master-SNAPSHOT"]]}
               }
    :plugins ~dev-deps
    :source-paths ["src/clj"]
    :java-source-paths ["src/java"]
    :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
;    :jvm-opts ["-Djline.internal.Log.trace=true"]
    :test-paths ["spec"]
    :repl-options {:init-ns user}
    :aot [reply.reader.jline.JlineInputReader]
    :main ^{:skip-aot true} reply.ReplyMain))
