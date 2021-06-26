(ns testcases.unused-ret-vals.red2
  (:import
   (java.io File)))

(defn foo []
  (try
    ;; Exercise `:unused-ret-vals-in-try` (static method call):
    (Class/forName "a")
    1
    (catch Exception _)))
