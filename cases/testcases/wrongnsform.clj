(ns testcases.wrongnsform
  ;; Clojure does not complain about the following line, but silently
  ;; turns it into a call to the println function.  Good to warn if
  ;; any but the documented set of keywords appear in an ns form.
  (:println "Hello, ns form!")
  ;; Clojure does not complain about putting ns references in vectors
  ;; rather than lists, but it goes against the ns doc string, and
  ;; tools.namespace ignores such things, leading to incorrect
  ;; determination of inter-namespace dependencies.
  [:use clojure.test]
  )


;; Clojure itself throws exception for this

;;(ns testcases.nsform2-exception
;;  :should-be-doc-string-or-attr-map-or-reference-but-isnt)

(ns testcases.nsform2
  [:use clojure.test])


(ns testcases.nsform3
  "doc string"
  ;; should-be-attr-map-or-reference-but-isnt
  [:use clojure.test])


(ns testcases.nsform4
  "doc string"
  {:author "me, part of ns attr-map"}
  ;; should-be-ns-reference-but-isnt
  [:use clojure.test])


(ns testcases.nsform5
  {:author "me, part of ns attr-map"}
  ;; should-be-ns-reference-but-isnt
  [:use clojure.test])


;; Example from Eastwood issue #98

;; Clojure 1.6.0 itself throws exception for this version:
;; java.lang.Exception: Unsupported option(s) supplied: :only, compiling:(filename.clj:LINE:COL)

;;(ns testcases.nsform6-exception
;;  (:require eastwood.util :as util :only (existing-var1 non-existing-var2)))

(ns testcases.nsform6
  (:require [eastwood.util :as util :only (existing-var1 non-existing-var2)]))
