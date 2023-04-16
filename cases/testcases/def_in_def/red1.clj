(ns testcases.def-in-def.red1)

(def foo
  (let [x 1]
    (def y x)
    x))
