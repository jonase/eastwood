(ns eastwood.test.testcases.f03
  (:use [clojure.tools.macro
         :only (with-symbol-macros defsymbolmacro name-with-attributes)]))


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
