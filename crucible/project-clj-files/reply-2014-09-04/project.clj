(let [dev-deps '[[speclj "2.7.2"]
                 [classlojure "0.6.6"]]]

  (defproject reply "0.3.6-SNAPSHOT"
    :description "REPL-y: A fitter, happier, more productive REPL for Clojure."
    :global-vars {*warn-on-reflection* true}
    :dependencies [[org.clojure/clojure "1.5.1"]
                   [jline "2.12"]
                   [org.thnetos/cd-client "0.3.6"]
                   [clj-stacktrace "0.2.7"]
                   [org.clojure/tools.nrepl "0.2.5"]
                   [org.clojure/tools.cli "0.3.1"]
                   [com.cemerick/drawbridge "0.0.6"
                    :exclusions [org.clojure/tools.nrepl]]
                   [trptcolin/versioneer "0.1.1"]
                   [clojure-complete "0.2.3"]
                   [net.cgrand/sjacket "0.1.1"
                    :exclusions [org.clojure/clojure]]]
    :min-lein-version "2.0.0"
    :profiles {:dev {:dependencies ~dev-deps}
               :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
               :1.7 {:dependencies [[org.clojure/clojure "1.7.0-master-SNAPSHOT"]]}}
    :plugins ~dev-deps
    :source-paths ["src/clj"]
    :java-source-paths ["src/java"]
    :javac-options ["-target" "1.5" "-source" "1.5" "-Xlint:-options"]
;    :jvm-opts ["-Djline.internal.Log.trace=true"]
    :test-paths ["spec"]
    :repl-options {:init-ns user}
    :aot [reply.reader.jline.JlineInputReader]
    :main ^{:skip-aot true} reply.ReplyMain))
