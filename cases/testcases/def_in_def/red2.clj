(ns testcases.def-in-def.red2)

(defn foo [x]
  (def bar x)
  x)
