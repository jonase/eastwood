(ns testcases.unused-fn-args.multimethods.green)

(defmulti foo
  (fn [a b c]
    ;; all args unintentionally unused
    (rand)))
