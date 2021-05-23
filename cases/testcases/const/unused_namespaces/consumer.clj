(ns testcases.const.unused-namespaces.consumer
  "https://github.com/jonase/eastwood/issues/192"
  (:require [testcases.const.unused-namespaces.producer :as a]))

(defn project-info []
  {:version a/version})
