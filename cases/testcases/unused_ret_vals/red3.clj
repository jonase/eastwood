(ns testcases.unused-ret-vals.red3
  (:import
   (java.io File)))

(defn foo []
  ;; Exercise `:unused-ret-vals` (instance method call):
  (-> "afs" .getBytes String. .toString)

  42)
