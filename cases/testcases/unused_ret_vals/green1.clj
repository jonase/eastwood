(ns testcases.unused-ret-vals.green1
  (:import
   (java.io File)))

(defn foo []
  ;; Exercise `:unused-ret-vals` (static method call):
  (File/createTempFile "a" "a")

  (try
    ;; Exercise `:unused-ret-vals-in-try` (static method call):
    (File/createTempFile "a" "a")
    1
    (catch Exception _))

  ;; Exercise `:unused-ret-vals` (instance method call):
  (-> "a" File. .delete)

  (try
    ;; Exercise `:unused-ret-vals-in-try` (instance method call):
    (-> "a" File. .delete)
    1
    (catch Exception _))

  42)
