(ns leiningen.eastwood
  (:use [leinjacker.eval :as leval]
        [leinjacker.deps :only [add-if-missing]]))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (let [opts (read-string opts)
           opts (assoc opts :source-paths (or (:source-paths opts)
                                              (:source-paths project)
                                              [(:source-path project)]))
           opts (assoc opts :java-source-paths (or (:java-source-paths opts)
                                                   (:java-source-paths project)
                                                   [(:java-source-path project)]))
           global-opts (:eastwood project)
           opts (merge global-opts opts)]
       (leval/eval-in-project (add-if-missing project '[jonase/eastwood "0.0.3"])
                              `(eastwood.core/run-eastwood '~opts)
                              '(require 'eastwood.core)))))

