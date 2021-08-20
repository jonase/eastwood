(ns testcases.f01
  (:use clojure.test))

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

(defn foo3  ; No doc string
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
     10))

(defn foo5
  ([x y]
     (str x y))
  ([]
     ;; This should be warned as a misplaced doc string, because it is
     ;; the first of more than one expression.
     "string3"
     10))

;; This test will not fail, because it will not be run, because the
;; same name will be redefined later.
(deftest test1-redefd
  (is (= 4 5)))

;; ditto
(deftest test1-redefd
  (is (= 0 1)))

(deftest test1-redefd
  (is (= 1 1)))

;; declare's should not count as def's for the purposes of detecting
;; repeated def's.
(declare i-am-not-redefd)

(defn i-am-also-not-redefd [x]
  (println x "Hello, World!")
  (if (zero? x)
    (i-am-not-redefd x)))

(defn i-am-not-redefd [y]
  (if (= y 5)
    (i-am-also-not-redefd (inc y))
    (inc y)))


;; def's not at the top level are harder to notice for analysis tools,
;; if you don't plan for it in advance.

(def i-am-redefd2 1)

(let [x 5]
  (def i-am-redefd2 (fn [y] (+ x y))))


;; :def-in-def test case

(def def-in-def1 1)

(defn bar [x]
  (let [foo-before def-in-def1]
    (def def-in-def1 (dec x))
    {:x x
     :def-in-def1-before foo-before
     :def-in-def1-now def-in-def1}))


;; :wrong-arity test cases

(defn call-with-wrong-num-of-args [x]
  (assoc x))

(defn call-with-wrong-num-of-args-2 [x]
  (assoc))

(defn call-local-fn-with-wrong-arity []
  (let [f1 (fn [x] (inc x))]
    (f1)))

(defn call-local-fn-with-wrong-arity2 []
  (let [f1 #(inc %2)]
    (f1)))


(defn catch
  "The Clojure compiler allows catch to be defined as a function and
called, as long as you do it from outside of a try form. Test case
for tools.analyzer, which at one time did not permit this. Zach
Tellman is to be credited/blamed for this test case."
  ([x y]
     (catch x Throwable y))
  ([x y z]
     [x y z]))


(defn fn-with-arglists-meta
  {:arglists '([x y])}
  [x y z]
  (+ x y z))


;; Eastwood can now detect the call to fn-with-arglists-meta below as
;; having the wrong number of arguments, if you add appropriate
;; configuration to override the :arglists metadata given above, which
;; makes it appear that it is called with the correct number of args
;; below.

;; This is a toy test example that I don't think anyone would create
;; in real development. It is only intended to verify that Eastwood
;; is actually using the configuration correctly.

(defn wrong-args1 [x]
  (fn-with-arglists-meta x (* 2 x)))


(defn wrong-args-threw-exception-before-bugfix1 [x]
  ((if x
     (fn [coll x] (conj coll x))
     (fn [coll x] (disj coll x)))
   #{:a :b} :c :d))


(defn wrong-args-threw-exception-before-bugfix2 [x]
  ((do
     (println "foo")
     (fn [coll x] (disj coll x)))
   #{:a :b} :c :d))


(def ^:dynamic *var1* [])

(def *var2* [])

(def ^:dynamic var3 [])

(def var4 [])
