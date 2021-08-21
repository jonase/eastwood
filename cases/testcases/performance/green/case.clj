(ns testcases.performance.green.case)

(defn foo [x]
  (case (long x)
    1 :one))
