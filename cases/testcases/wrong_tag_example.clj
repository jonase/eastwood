(ns testcases.wrong-tag-example)

(defmacro the-macro [n x]
  `(defn ~n ~(with-meta [] {:tag pos?})
     ~x))

(the-macro foo 42)
