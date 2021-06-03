(ns testcases.unused-fn-args.multimethods.red)

(defn foo []
  (fn [a b c]
    ;; all args unintentionally unused
    (rand)))
