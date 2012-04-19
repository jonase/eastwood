(ns leiningen.eastwood
  (:use [leiningen.core.eval :only [eval-in-project]]))

(defn prepare-project [project]
  (let [project (update-in project
                           [:dependencies]
                           conj
                           '[jonase/eastwood "0.0.1"])

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



