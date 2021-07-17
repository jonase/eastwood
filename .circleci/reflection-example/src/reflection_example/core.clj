(ns reflection-example.core)

(defn- omg [x]
  (.toString x))

(omg 2)
(omg "")

(defn boo []
  (omg 2)
  (omg ""))

(defmacro foo [x]
  `(.thing ~x))

(defn bar [x]
  (.refl x))
