(ns testcases.unhinted-reflective-call.defn-call-inside-refresh-dirs
  (:require
   [testcases.unhinted-reflective-call.example-defn]))

(defn foo [x]
  (testcases.unhinted-reflective-call.example-defn/foo x))
