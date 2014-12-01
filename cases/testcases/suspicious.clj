(ns testcases.suspicious
  (:import (java.io StringReader))
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

(-> 1)
(->> 1)
(and)
(and 1)
(as-> 1 x)
(case 5 2)
(cond)
(let [x 5] (cond-> x))
(let [x 5] (cond->> x))
(condp = 5 2)
(declare)
(delay)
(doseq [x [1 2 3]])
(dotimes [i 10])
(doto (Object.))
(import)
(lazy-cat)
(let [x 5])
(letfn [(foo [x] (inc x))])
(locking (Object.))
(loop [x 1])
(or)
(or 1)
(pvalues)
(some-> 5)
(some->> 5)
(when 5)
(when-first [x [5]])   ; tbd: Extra let warning to be suppressed
(when-let [x 5])       ; tbd: Extra let warning to be suppressed
(when-not 5)
(when-some [x 5])      ; tbd: Extra let warning to be suppressed
(with-bindings {#'*warn-on-reflection* false})
(with-in-str "foo")
(with-local-vars [x 5])
(with-open [rdr (java.io.StringReader. "foo")])
(with-out-str)
(with-precision 10)
(with-redefs [*warn-on-reflection* false])
