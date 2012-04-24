(ns leiningen.eastwood
  (:use [leinjacker.eval-in-project :only [eval-in-project]]
        [leinjacker.deps :only [add-if-missing]]))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (let [opts (read-string opts)
           opts (assoc opts :source-paths (or (:source-paths opts)
                                              (:source-paths project)
                                              [(:source-path project)]))
           global-opts (:eastwood project)
           opts (merge global-opts opts)]
       (eval-in-project (add-if-missing project '[jonase/eastwood "0.0.3"])
                        `(eastwood.core/run-eastwood '~opts)
                        '(require 'eastwood.core)))))

