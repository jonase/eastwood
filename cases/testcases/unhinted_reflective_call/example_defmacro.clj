(ns testcases.unhinted-reflective-call.example-defmacro)

(defmacro foo [x]
  `(.sdfsdf ~x))
