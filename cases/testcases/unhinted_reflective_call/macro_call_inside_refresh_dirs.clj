(ns testcases.unhinted-reflective-call.macro-call-inside-refresh-dirs
  (:require
   [testcases.unhinted-reflective-call.example-defmacro]))

(defn foo [x]
  (testcases.unhinted-reflective-call.example-defmacro/foo x))
