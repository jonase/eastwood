(ns testcases.warnloc1)

;; There are many blank lines in this file before the actual code,
;; because I want the line numbers to be larger than any line numbers
;; possible in the namespace testcases.warnloc2


;; The sole purpose of this namespace and testcases.warnloc2 is to
;; debug, and eventually fix and provide ongoing test cases for,
;; incorrect file/line/column combinations that as of just before
;; Eastwood 0.1.5 release are still there.  That is, sometimes a file
;; name is reported with a line number that does not exist in that
;; file at all.  It appears that this is due to an example like this:

;; Namespace A defines a macro.

;; Namespace B requires namespace A, then uses the macro in A.

;; The macro invocation, when expanded, causes Eastwood to issue a
;; warning on something about the macroexpanded form.

;; When Eastwood looks for :file :line :column keys in metadata, it
;; finds a line number in namespace A, but the file name for namespace
;; B.

;; I am not sure exactly how this happens, but it might be when
;; namespace B is on the list of namespaces to be linted explicitly,
;; and is thus read with tools.reader, but namespace A is *not* on the
;; list of namespaces to be linted, and is thus read using Clojure's
;; built-in reader, which does not have as extensive of metadata on
;; it.


;; many blank lines here.  Go to the end for actual code.















































(defmacro mydo [& args]
  `(do ~@args))
