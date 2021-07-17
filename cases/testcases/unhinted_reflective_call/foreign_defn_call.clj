(ns testcases.unhinted-reflective-call.foreign-defn-call
  (:require
   [reflection-example.core]))

(defn bar [x]
  (reflection-example.core/bar x))
