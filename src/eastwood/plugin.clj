(ns eastwood.plugin
  (:require [eastwood.config :as config]
            [eastwood.copieddeps.dep6.leinjacker.eval :as leval]))

(defn middleware
  [project]
  (let [opts (:eastwood project)]
    (leval/eval-in-project 
      project
      (intern
        'eastwood.config
        'options
        opts))
  project))
