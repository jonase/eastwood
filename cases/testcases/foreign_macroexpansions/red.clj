(ns testcases.foreign-macroexpansions.red
  (:require [eastwood.test.outside-test-paths.example :refer [faulty]]))

(defn foo [x]
  (faulty x))
