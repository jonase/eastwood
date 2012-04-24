(ns leiningen.eastwood
  (:use [leinjacker.eval-in-project :only [eval-in-project]]
        [leinjacker.deps :only [add-if-missing]]))

(defn prepare-project [project]
  (let [project (add-if-missing project '[jonase/eastwood "0.0.3"])
        project (if (contains? project :source-path)
                  (assoc project :source-paths [(:source-path project)])
                  project)]
    project))

(defn eastwood
  ([project] (eastwood project "{}"))
  ([project opts]
     (let [project (prepare-project project)
           opts (read-string opts)
           opts (if (contains? opts :source-paths)
                  opts
                  (assoc opts :source-paths (:source-paths project)))
           global-opts (:eastwood project)
           opts (merge global-opts opts)]
       (eval-in-project project
                        `(eastwood.core/run-eastwood '~opts)
                        '(require 'eastwood.core)))))

