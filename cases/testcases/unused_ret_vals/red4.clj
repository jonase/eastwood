(ns testcases.unused-ret-vals.red4
  (:import
   (java.io File)))

(defn foo []
  (try
    ;; Exercise `:unused-ret-vals-in-try` (instance method call):
    (-> "afs" .getBytes String. .toString)
    1
    (catch Exception _)))
