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


;; Clojure 1.6.0 throws exception for these requires with
;; namespace-qualified symbols.  It actually does load the namespace
;; specified by the symbol without the namespace qualifier, but throws
;; an exception afterwards when it checks that the namespace exists,
;; and finds that the namespace with the qualifier does not exist.

;;(ns testcases.nsform6-exception
;;  (:require foo/eastwood.passes)
;;  (:require [foo/eastwood.util :as util :only (existing-var1 non-existing-var2)]))

;; Clojure 1.6.0 gives no exception for the following ns form, and
;; quietly ignores the (eastwood.foo) because it is a prefix with no
;; libspecs after it.  It does not matter if what is inside the parens
;; is a valid namespace name or not.  This _should_ be warned about by
;; Eastwood, since the author probably intended a bare symbol, or a
;; libspec in a vector, instead.

(ns testcases.nsform7
  (:require (eastwood.foo)))

;; This is a valid prefix list that tries to load namespace
;; eastwood.util.  There should be no warning for it.

(ns testcases.nsform7b
  (:require (eastwood util)))

;; Clojure 1.6.0 throws an exception during eval for this case, so no
;; warning from Eastwood will appear unless it is issued pre-eval.
;; Current exception is:

;; java.lang.Exception: prefix cannot be nil, compiling(filename.clj:LINE:COL)

;; No such pre-eval Eastwood warnings are implemented for this, at
;; least not yet.

;;(ns testcases.nsform7c-exception
;;  (:require ()))


;; Clojure 1.6.0 throws exception for these, "nth is not supported on
;; the type: <foo>" where <foo> is PersistentArrayMap,
;; PersistentHashSet

;;(ns testcases.nsform8-exception
;;  (:require {eastwood.foo true}))

;;(ns testcases.nsform9-exception
;;  (:require #{eastwood.foo}))


;; Clojure 1.6.0 throws an exception for this, but it is not terribly
;; clear from the exception what the problem is:

;; "java.lang.ClassCastExeption: clojure.lang.Symbol cannot be cast to clojure.lang.Keyword, compiling(filename.clj:LINE:COL)"

;; TBD: For Eastwood to give a clearer warning, it would either need
;; to do pattern matching on the exception thrown, perhaps including
;; the stack trace and form being eval'd when it was thrown, or check
;; the ns form after being read, and maybe also after being analyzed,
;; but before being eval'd.  After analysis but before eval would
;; require modifying analyze+eval.  After read but before analyze+eval
;; would miss non-top-level ns forms, but those are quite rare.

;;(ns testcases.nsform10-exception
;;  (:require [:eastwood]))


;; These flags are valid in require forms, but it is most common to
;; use them interactively, not in ns forms in a source file, so nice
;; to warn about them.

(ns testcases.nsform11
  (:require [eastwood.util] :reload))


;; Clojure 1.6.0 throws an exception for this, and the message is clear:

;; java.lang.Exception: Unsupported option(s) supplied: :foo, compiling:(filename.clj:LINE:COL)

;;(ns testcases.nsform12-exception
;;  (:require [eastwood.util] :foo))


;; Clojure 1.6.0 with the :require below causes clojure.core/load-lib
;; to be called with the arguments: prefix=nil, lib=eastwood.util,
;; options=(:as :require true).  When it tries to call (apply hash-map
;; option) with an odd number of arguments, it throws the exception:

;; IllegalArgumentException No value supplied for key: true

;; This makes sense upon seeing the value of options, but it is a very
;; confusing error message that does not help find the source of the
;; problem.

;;(ns testcases.nsform13-exception
;;  (:require [eastwood.util :as]))



;; Example from Eastwood issue #98

;; Clojure 1.6.0 itself throws exception for this version:
;; java.lang.Exception: Unsupported option(s) supplied: :only, compiling:(filename.clj:LINE:COL)

;;(ns testcases.nsform14-exception
;;  (:require eastwood.util :as util :only (existing-var1 non-existing-var2)))

(ns testcases.nsform15
  (:require [eastwood.util :as util :only (existing-var1 non-existing-var2)]))


;; Ensure that we do not warn for :refer :all, even though :all is not
;; a list of symbols.

(ns testcases.nsform16
  (:require [eastwood.util :as util :refer :all]))


;; Even though the doc string for require says that it only takes the
;; option keys :as and :refer, it appears that it might also do
;; something reasonable for the use-documented option keys of :rename
;; and :exclude

;; These examples are from namespace clojure.tools.analyzer.jvm

;; Eastwood has been programmed not to warn about :exlude or :rename
;; option keys to a :require libspec, but only if it uses the :refer
;; option key.

(ns testcases.nsform17
  (:require [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm.utils :refer :all :exclude [box]]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer
             :as ana
             :refer [analyze analyze-in-env wrapping-meta analyze-fn-method]
             :rename {analyze -analyze}]))
