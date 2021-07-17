(ns testcases.unhinted-reflective-call.defn-call-outside-refresh-dirs
  (:require
   [eastwood.test.outside-test-paths.defn-reflection-warning]))

(defn foo [x]
  (eastwood.test.outside-test-paths.defn-reflection-warning/foo x))
