(ns testcases.unhinted-reflective-call.macro-call-outside-refresh-dirs
  (:require
   [eastwood.test.outside-test-paths.reflection-warning]))

(defn foo [x]
  (eastwood.test.outside-test-paths.reflection-warning/foo x))
