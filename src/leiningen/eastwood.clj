(ns leiningen.eastwood
  (:require [leinjacker.eval :as leval]
            [leinjacker.deps :refer [add-if-missing]]))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
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
       (leval/eval-in-project (add-if-missing project '[jonase/eastwood "0.1.1-SNAPSHOT"])
                              `(eastwood.versioncheck/run-eastwood '~opts)
                              '(require 'eastwood.versioncheck)))))

