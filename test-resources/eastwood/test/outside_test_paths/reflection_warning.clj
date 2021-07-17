(ns eastwood.test.outside-test-paths.reflection-warning)

(defmacro foo [x]
  `(.bar ~x))
