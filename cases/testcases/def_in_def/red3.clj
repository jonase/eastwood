(ns testcases.def-in-def.red3)

(defmacro my-macro [& body]
  `(do
     ~@body))

(my-macro (def foo
            (let [x 1]
              (def bar x)
              x)))
