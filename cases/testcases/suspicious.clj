(ns testcases.suspicious
  (:use [clojure.test]))

;; Test cases targeted at :suspicious-expression linter.  There are
;; some more in testcases.testtest namespace.


;; Empty defrecords have macroexpansions containing suspicious-looking
;; macro invocations at intermediate steps, which would be good not to
;; warn about.
(defrecord zero-type [])

;; and/or have 1-arg and/or at penultimate expansion step
(if (and (> 5 3) (> 5 7)) 3 4)
(if (or (> 5 7) (> 5 3)) 5 6)

;; cond has 0-arg version in itsexpansion
(cond (> 5 3) 7
      (> 5 7) 3)

;; Earlier I had a bug that suppressed warnings for these
(or (> 5 3)
    (doto (StringBuffer.)))
