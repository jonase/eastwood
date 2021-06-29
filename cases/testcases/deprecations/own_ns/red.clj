(ns testcases.deprecations.own-ns.red
  (:require
   [testcases.deprecations.own-ns.green :as green]))

(defn foo []
  (green/foo))
