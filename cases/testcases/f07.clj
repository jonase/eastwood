(ns testcases.f07
  (:use clojure.test)   (:import (org.apache.commons.io IOUtils))
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.shell :as sh]
            [clojure.pprint :as p]))

(defn unused-ret-vals57 []
  (try
    (+ 5 7)
    (apply inc 1)
    (catch Exception e
      e)
    (finally 18)))

;; A - in the middle of a protocol method name once caused
;; tools.analyzer to throw an exception. Keep this around for
;; regression testing.
(defprotocol BarBary
  (as-file [x]))

(defn unused-ret-vals33 [i n]
  (let [a (* i n)]
    (+ i n i n i)
    (*' n i)
    (quot i n)
    {:a (dissoc {:b 2} :b), (map inc [1 2 3]) 7}
    [(assoc {} a n) (/ i a)]
    (dec i)))

(defn unused-ret-vals-calls [x y]
  (assoc {} x y)
  (apply + [1 2 3])
  (filter print [1 2 3])
  (disj! (transient #{:a :b}) :a)
  (- x y x)
  (+ x y))


;; It would be nice if the (dec i) expression caused an
;; :unused-ret-vals warning, but it currently does not because the
;; doseq body has a do wrapped around it. Eastwood isn't clever
;; enough to notice that the enclosing do has its return value
;; ignored, and thus the final expression within the do also has its
;; return value ignored.

;; TBD: The :op for the body of the doseq is :do. If we find that,
;; then the return values of every expression in the do are also
;; ignored, so iterate through them, doing the same checks on all
;; subforms. Go through the :statements sequence elements, and the
;; :ret child, too. Even the :ret child has its return value ignored,
;; because the do form as a whole does.

;; Actually the statements will already be gone through. It is only
;; the :ret child that is not already covered elsewhere.

(defn unused-ret-vals2 [i n]
  (doseq [j (range n)]
    (println i j)
    (dec i)))

(defn unused-ret-vals2b [i n]
  (doseq [j (range n)]
    (println j)
    (assoc {} i i)))


;; This case is similar to the previous one, except it is a []
;; wrapping around the pure functions. It 'uses' their return values,
;; but its own value is itself discarded. Obviously this could extend
;; through any number of levels of nesting.

;; The statement containing the vector has :op :with-meta, :tag
;; clojure.lang.PersistentVector, :children [:meta :expr], and the
;; :expr is itself a sub-ast with :op :vector, :children [:items], and
;; :items is a vector of ast's, each of which should be iterated
;; through. The first is an :invoke, the second a :static-call.

(defn unused-ret-vals3 [i n]
  (let [a (* i n)]
    {:a (dissoc {:b 2} :b)}
    [(assoc {} a n) (/ i a)]
    (dec i)))


;; In a case where a larger aggregate of code's return value is
;; discarded, but it consists of a mix of pure and side-effecting
;; functions, it would be nice to warn about the whole thing, and each
;; pure function that is part of it.

;; The statement containing the set below has :op :set, :tag
;; clojure.lang.IPersistentSet, :children [:items], and :items is a vector of ast's with :op values 

;; for assoc, :invoke
;; for (/ i n), :static-call
;; for (+ 3 n), :static-call
;; for (println "foo"), :invoke

(defn unused-ret-vals4 [i n]
  #{(assoc {} i n) (/ i n) (+ 3 n) (println "foo")}
  (dec i))


;; The assoc expression below is warned about. Good.

(defn unused-ret-val4 [k v]
  (assoc {} k v)
  [k v])

(defn gah [n]
  (let [x (* 2 n)]
    repeatedly n x #(rand-int 100)))

(defn check-do-let-nesting [a]
  ;;(let [b 2]
  (do
    (println "no warning for me")
    ;;(let [c 3]
    (do
      (println "nor for me")
      ;; The gah on the following line should get an unused warning
      ;; because even though it is in return position for the
      ;; enclosing let, and that let is in return position for its
      ;; enclosing let, that enclosing let has an unused return value.
      gah))
  (/ a 2))

;; Marker protocols and interfaces should not generate any warnings

(defprotocol MarkerProtocol)

(definterface IMarkerInterface)

(defmacro compile-if [test then else]
  (if (eval test)
    then
    else))

(defn more-unused-ret-vals [a]
  (compile-if (resolve 'clojure.core/some?)
    (some? a)
    (false? a))
  (true? a))

;; comment is a normal macro, not built-in to the Clojure compiler in
;; any more special way than that. It expands to nil. Eastwood has a
;; special case for this in its :unused-ret-vals linter that should
;; prevent warnings for the comment forms below.

(let [x 5]
  ;; nil by itself here causes an :unused-ret-vals warning, but not if
  ;; it is wrapped in a comment.
  (comment nil)
  (defn bar2 [y]
    (comment 1)
    (- 5 y x))
  (comment "flamtacious")
  7)

(defn unused-ret-vals34 [i n]
  (let [a (* i n)]
    (compile-if (resolve 'clojure.string/includes?)
      (str/includes? "food" "oo")
      ;; I'd like it if Eastwood warned about the return value of the
      ;; .contains call below not being used, too, but currently the
      ;; unused-ret-vals linter only handles static method calls, not
      ;; instance method calls.
      (.contains "food" "oo"))
    a))

;; As of Eastwood 0.2.2, passes/get-method when called on
;; the (IOUtils/closeQuietly out) static method call can call
;; .getMethod and .getDeclaredMethod with
;; args (Class/forName "org.apache.commons.io.IOUtils") "closeQuietly" (into-array
;; Class [(Class/forName "java.io.PrintWriter")]), for which it does
;; not find a method (it does if the last argument is changed to
;; PrintWriter's superclass, Writer).

;; This leads to the unused-ret-vals linter, which calls
;; passes/get-method and then passes/void-method? on the return value,
;; to throw an exception. A quick fix for Issue #173 is to only call
;; passes/void-method? if the return value of passes/get-method is a
;; Method, and otherwise never give an :unused-ret-val warning for the
;; method call. It would be preferable to find a way to resolve the
;; method call even better, but I'm not sure how to do that in
;; general, with multiple arguments of different types.

(defn issue-173-test
  [connect-fn prefix ^java.io.PrintWriter out rollup]
  (try
    (let [socket (if (and out (not (.checkError out)))
                   out
                   (do (IOUtils/closeQuietly out)
                       (connect-fn)))]
      (println prefix socket rollup 0.0))
    (catch Exception e
      (IOUtils/closeQuietly out))))
