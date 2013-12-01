(ns eastwood.test.testcases.try4)

(def i-am-redefd 1)
(def i-am-redefd (fn [y] (inc y)))

(defn foo1
  "Good doc string placement"
  [x]
  (dec x))

(defn foo2
  [x]
  "Bad doc string placement"
  (* x x))

(defn foo3
  [x]
  (/ x 3))

(defn foo4
  ([x]
     ;; This should not be warned as a misplaced doc string, because
     ;; it is in return position.
     "string1")
  ([x y]
     (str x y))
  ([]
     ;"string3"
     10))

(defn foo5
  ([x y]
     (str x y))
  ([]
     ;; This should be warned as a misplaced doc string, because it is
     ;; the first of more than one expression.
     "string3"
     10))
