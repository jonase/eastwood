(ns testcases.unused-ret-vals.red1
  (:import
   (java.io File)))

(defn foo []
  ;; Exercise `:unused-ret-vals` (static method call):
  (Class/forName "a")

  3)
