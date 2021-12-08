(ns example-project.core
  (:require
   [clojure.tools.namespace.repl :refer [set-refresh-dirs]]))

;; no `"red"`.
;; The expectation is that `"green"` will be analysed, and it will pass even though it `require`s the `red` ns,
;; because red is not in the refresh-dirs, or the Eastwood config.
(set-refresh-dirs "src" "green")
