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
        comment (fn [y] (println name map y))
        remove #(inc %)]
    ;; No warning, similar to (remove 5) call case below.
    (comment 7)
    ;; should cause a warning for pmap, but current linter doesn't
    ;; detect that pmap is being used as a function here.
    (println (map pmap [1 2 3]))
    ;; Ideally this should not cause a warning.  Figure out how to
    ;; determine that remove is a function, and consider this an
    ;; intentional case of shadowing.
    (println (remove 5))))

;; core.logic intentionally shadows the name loop using letfn.
;; Hopefully the result of analyzing this code makes it easy to
;; determine that loop's value is a function in this case, so I can
;; suppress the warning.

(defn shadowed-loop [v]
  (letfn [(loop [ys]
            (if ys
              (loop (next ys))
              28))]
    (loop v)))
