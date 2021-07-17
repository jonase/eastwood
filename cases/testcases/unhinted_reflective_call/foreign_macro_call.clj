(ns testcases.unhinted-reflective-call.foreign-macro-call
  (:require
   [reflection-example.core]))

(defn foo [x]
  (reflection-example.core/foo x))
