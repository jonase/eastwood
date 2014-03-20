(ns leiningen.eastwood
  (:require [leinjacker.eval :as leval]
            [leinjacker.deps :refer [add-if-missing]]))

(defn help []
  "Lint your Clojure code.")

(defn eastwood-help []
  (println "lein eastwood: Lint your Clojure code.
Usage: To lint all Clojure files in your :source-paths and :test-paths:

    lein eastwood

WARNING: If loading your code, e.g. via 'require' or 'use' (especially
test files) causes side effects like opening connections to servers,
modifying databases, etc., linting your test files will have those
side effects, too.  To confine linting to Clojure code in
your :source-paths, use this command:

    lein eastwood '{:namespaces [:source-paths]}'

For other options, see the full documentation on-line here:

    https://github.com/jonase/eastwood
")
  (flush))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (if (= opts "help")
       (eastwood-help)
       (let [opts (read-string opts)
             opts (assoc opts :source-paths (or (:source-paths opts)
                                                (:source-paths project)
                                                [(:source-path project)]))
             opts (assoc opts :test-paths (or (:test-paths opts)
                                              (:test-paths project)
                                              [(:test-path project)]))
             opts (assoc opts :java-source-paths (or (:java-source-paths opts)
                                                     (:java-source-paths project)
                                                     [(:java-source-path project)]))
             global-opts (:eastwood project)
             opts (merge global-opts opts)]
         ;; eastwood-version on next line
         (leval/eval-in-project (add-if-missing project '[jonase/eastwood "0.1.1"])
                                `(eastwood.versioncheck/run-eastwood '~opts)
                                '(require 'eastwood.versioncheck))))))
