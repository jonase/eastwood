(ns testcases.in-ns-switching
  ;;(:require [testcases.f01 :as t1])
  (:use clojure.test
        clojure.set
        [testcases.f01 :as t1]
        ))

;; This test is inspired by a problem found when analyzing Incanter's
;; namespace incanter.sql-tests. It has a similar use of in-ns to
;; change namespaces, fiddle with something in the other namespace,
;; then switch back to the original namespace with another in-ns call.
;; Earlier versions of Eastwood would correctly process the first
;; in-ns, but would not process the second.

(in-ns 'testcases.f01)

(def statements-made (clojure.core/atom []))

(in-ns 'testcases.in-ns-switching)

;; Verify that the second in-ns was processed correctly by referring
;; to a var by a symbol that would only work if the second in-ns
;; worked.

(def s1 (union #{1 2} #{3 4}))
