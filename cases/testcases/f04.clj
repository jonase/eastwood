(ns testcases.f04)


;; This case used to cause tools.analyzer to throw an exception before
;; ticket TANAL-12 was fixed.

(try
  (fn foo
    ([]
       nil)
    ([x]
       (if (< x 5)
         (println x)
         (recur (inc x)))))
  (catch Exception e
    (println "Exception occurred")))
