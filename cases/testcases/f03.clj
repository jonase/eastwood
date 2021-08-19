(ns testcases.f03
  (:import (java.io InputStream File FileOutputStream)
           (java.net URI))
  (:require clojure.main clojure.tools.macro)
  (:use [clojure.tools.macro
         :only (with-symbol-macros defsymbolmacro name-with-attributes)])
  (:gen-class))   ; used to cause :unused-ret-vals warning

;; This case used to cause tools.analyzer to throw an exception before
;; ticket TANAL-11 was fixed.

(defmacro defmonadfn
  "Like defn, but for functions that use monad operations and are used inside
   a with-monad block."
  {:arglists '([name docstring? attr-map? args expr]
               [name docstring? attr-map? (args expr) ...])}
  [name & options]
  (let [[name options]  (name-with-attributes name options)
        fn-name (symbol (str *ns*) (format "m+%s+m" (str name)))
        make-fn-body    (fn [args expr]
                          (list (vec (concat ['m-bind 'm-result
                                              'm-zero 'm-plus] args))
                                (list `with-symbol-macros expr)))]
    (if (list? (first options))
      ; multiple arities
      (let [arglists        (map first options)
            exprs           (map second options)
            ]
        `(do
           (defsymbolmacro ~name (partial ~fn-name ~'m-bind ~'m-result
                                                   ~'m-zero ~'m-plus))
           (defn ~fn-name ~@(map make-fn-body arglists exprs))))
      ; single arity
      (let [[args expr] options]
        `(do
           (defsymbolmacro ~name (partial ~fn-name ~'m-bind ~'m-result
                                                   ~'m-zero ~'m-plus))
           (defn ~fn-name ~@(make-fn-body args expr)))))))

(defsymbolmacro m-bind m-bind)

(defmonadfn m-join
  "Converts a monadic value containing a monadic value into a 'simple'
   monadic value."
  [m]
  (m-bind m identity))


;; Excerpted from clojure.java.io. Once caused tools.analyzer to
;; throw an exception. Reported and fixed as ticket TANAL-19.

(defmulti do-copy
  (fn [input output _opts] [(type input) (type output)]))

(defmethod do-copy [InputStream File] [^InputStream input ^File output opts]
  (with-open [out (FileOutputStream. output)]
    (do-copy input out opts)))


;; This function, excerpted and cut down from clojure.java.browse,
;; once caused tools.analyzer to throw an exception. It was reported
;; and fixed as ticket TANAL-20.

(defn open-url-in-browser [url]
  (try
    (-> (clojure.lang.Reflector/invokeStaticMethod "java.awt.Desktop"
                                                   "getDesktop" (to-array nil))
        (.browse (URI. url)))
    url
    (catch ClassNotFoundException e
      nil)))


;; Stripped down from a longer example in namespace clojure.data.xml.
;; Exception in tools.analyzer was reported and fixed as ticket
;; TANAL-21.

(defn foo1 []
  javax.xml.transform.OutputKeys/INDENT)

(defn foo2 []
  (javax.xml.transform.OutputKeys/INDENT))


;; Stripped down from some example found somewhere in a contrib
;; library, I think. Formerly caused exception with tools.analyzer,
;; but reported and fixed as ticket TANAL-25.

(defn foo [e]
  (clojure.main/repl-caught e))

;; The comment below used to cause an :unused-ret-vals warning in
;; earlier versions of Eastwood. Starting with version 0.1.4,
;; tools.analyzer(.jvm) added :raw-forms in the returned ASTs, making
;; it easy for Eastwood to discover that the nil return value was
;; produced from a comment macro invocation.

;; The other local bindings of comment as fn and macro are there
;; simply to see what tools.analyzer(.jvm) will return for them.

(defn bar [x]
  (comment 1 2 3)
  (let [comment (fn [y] (println y))]
    (comment 7))
  (clojure.tools.macro/macrolet [(comment [y] `(println ~y))]
    (comment 9))
  (inc x))
