(ns testcases.f07
  (:use clojure.test)
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
;; tools.analyzer to throw an exception.  Keep this around for
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
;; doseq body has a do wrapped around it.  Eastwood isn't clever
;; enough to notice that the enclosing do has its return value
;; ignored, and thus the final expression within the do also has its
;; return value ignored.

;; TBD: The :op for the body of the doseq is :do.  If we find that,
;; then the return values of every expression in the do are also
;; ignored, so iterate through them, doing the same checks on all
;; subforms.  Go through the :statements sequence elements, and the
;; :ret child, too.  Even the :ret child has its return value ignored,
;; because the do form as a whole does.

;; Actually the statements will already be gone through.  It is only
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
;; wrapping around the pure functions.  It 'uses' their return values,
;; but its own value is itself discarded.  Obviously this could extend
;; through any number of levels of nesting.

;; The statement containing the vector has :op :with-meta, :tag
;; clojure.lang.PersistentVector, :children [:meta :expr], and the
;; :expr is itself a sub-ast with :op :vector, :children [:items], and
;; :items is a vector of ast's, each of which should be iterated
;; through.  The first is an :invoke, the second a :static-call.

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


;; The assoc expression below is warned about.  Good.

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

(defn more-unused-ret-vals [a]
  (some? a)
  (true? a))
