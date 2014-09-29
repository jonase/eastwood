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


;; Below are test cases for :local-shadows-var linter
(defn bar [x]
  (comment 1 2 3)
  ;; The let-bindings of name and filter should not cause
  ;; :local-shadows-var linter warnings, as long as they are never
  ;; used as functions.
  (let [name 'foo
        pmap {:a 1 :b 2}
        comment (fn [y] (println name map y))]
    ;; This use of comment should cause a warning with the current
    ;; linter, although the fact that comment is explicitly defined
    ;; above as a fn should perhaps not cause a warning if the linter
    ;; were 'smarter'.
    (comment 7)
    ;; should cause a warning for pmap, but current linter doesn't
    ;; detect that pmap is being used as a function here.
    (map pmap [1 2 3])))
