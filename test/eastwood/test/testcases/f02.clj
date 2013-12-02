(ns eastwood.test.testcases.f02
  (:use clojure.test))


;; defonce and defmulti currently require special handling in the
;; eastwood implementation to avoid :redefd-vars warnings.

(defonce i-am-defonced-and-defmultid 'foo)

(defmulti i-am-defonced-and-defmultid)

(defonce i-am-defonced 7)

(defmulti i-am-a-defmulti-done-once)

(defmulti i-am-a-redefd-defmulti)

(defmulti i-am-a-redefd-defmulti)

(defonce i-am-outer-defonce-sym (defonce i-am-inner-defonce-sym 7))


;; It seems defprotocol macroexpands into a do with quite a few
;; sequential forms, each with side effects.  It is probably necessary
;; to analyze, and then eval, each of them one at a time, in order for
;; things to work.

(defprotocol ProtocolNameNotRedefd
  "This protocol is only defined once."
  (as-file [x] "Coerce argument to a file."))


;; This used to cause a problem for tools.analyzer, but it has since
;; been filed as ticket TANAL-6 and fixed.

(defn pprint-matrix []
  (doseq [[i row] (map (fn [i] [i 1]) [5 6 7])]
    (doseq [p (take i (range))]
      (print p))
    (print i)))


;; This was a test case that used to be a problem for tools.analyzer
;; before ticket TANAL-10 was fixed.

(deftest update2
  (str #'update2))
