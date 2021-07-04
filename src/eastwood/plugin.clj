(ns eastwood.plugin
  (:require [eastwood.config :as config]
            [eastwood.copieddeps.dep6.leinjacker.eval :as leval]))

(defn middleware
  [project]
  (let [opts (or (:eastwood project) {})]
    (update-in project [:injections] concat
               `[(require 'eastwood.config)
                 (intern 'eastwood.config '~'options ~opts)])))
