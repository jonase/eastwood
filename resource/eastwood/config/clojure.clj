;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in clojure.core, version 1.6.0
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of #{'clojure.core/defonce
                                 'clojure.core/defmulti
                                 'mount.core/defstate}
  :within-depth 2
  :reason "defonce, defmulti expand to code with multiple def's for the same Var."})

(disable-warning
 {:linter :unused-fn-args
  :if-inside-macroexpansion-of #{'clojure.core/defmulti}
  :within-depth 4})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-first}
  :within-depth 6
  :reason "when-first with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-let}
  :within-depth 6
  :reason "when-let with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-some}
  :within-depth 3
  :reason "when-some with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'clojure.spec/every 'clojure.spec.alpha/every
                                 'clojure.spec/and 'clojure.spec.alpha/and
                                 'clojure.spec/keys 'clojure.spec.alpha/keys
                                 'clojure.spec/coll-of 'clojure.spec.alpha/coll-of}
  :within-depth 6
  :reason "clojure.spec's macros `keys`, `every`, and `and` often contain `clojure.core/and` invocations with only one argument."})

(disable-warning
 {:linter :constant-test
  :for-macro 'clojure.core/or
  :if-inside-macroexpansion-of #{'clojure.spec/coll-of 'clojure.spec.alpha/coll-of}
  :within-depth 8
  :reason "clojure.spec's `coll-of` can contain `clojure.core/or` invocations with only one argument."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.test/is}
  :within-depth 2
  :reason "`is` can contain arbitrary exprs that are \"constant-looking\". They typically intend to exercise a predicate."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.test/is}
  :within-depth 3
  :qualifier false
  :reason "Support (is false) idiom https://github.com/jonase/eastwood/issues/384"})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.test/are}
  :reason "`are` can template arbitrary expressions. Therefore one cannot (easily) know if a given expression came from a template, and therefore, whether it's a truly 'constant' expression."})

(doseq [qualifier [true :always]]
  (disable-warning
   {:linter :constant-test
    :if-inside-macroexpansion-of #{'clojure.core/cond-> 'clojure.core/cond->>}
    :qualifier qualifier
    :within-depth 2
    :reason "Allow cond-> and cond->> to have constant tests without warning, for specific conditions"}))

(disable-warning
 {:linter :unused-ret-vals
  :if-inside-macroexpansion-of #{'clojure.core/with-out-str}
  :reason "Arbitrary expressions within `with-out-str` can have side-effects,
their intention being to write to stdout and therefore have the `with-out-str` result affected."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.core/as->}
  :within-depth 2
  :reason "Allow as-> to have constant tests without warning"})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.core/some-> 'clojure.core/some->>}
  :within-depth 2})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.core/while}
  :within-depth 3
  :qualifier true
  :reason "Allow `(while true)` idiom"})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'nedap.utils.spec.impl.check/throw-validation-error
                                 'nedap.utils.spec.impl.check/check!}})

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'clojure.core/eduction
  :arglists-for-linting '([& xform])
  :reason "eduction takes a sequence of transducer with a collection as the last item"})

(disable-warning
 {:linter :wrong-arity
  :if-inside-macroexpansion-of #{'clojure.core.reducers/rfn}
  :reason "`'clojure.core.reducers/rfn` assumes arity 0 even if it doesn't exist"})

(disable-warning
 {:linter :suspicious-test
  :if-inside-macroexpansion-of #{'clojure.test/are}
  ;; only omit the warning if :suspicious-test failed due to a `true` value:
  :qualifier true
  :within-depth 9
  :reason "Support a specific pattern that tends to fail better."})

(do
  ;; All these disablings within `go` are here because it's a peculiar macro which seemingly doesn't integrate well with the :if-inside-macroexpansion-of implementation.
  ;; These disablings can duplicate logic placed elsewhere.
  ;; This section might have to grow in a future. Maybe there's a way to de-duplicate the disablings or impl logic.

  (disable-warning
   {:linter :suspicious-expression
    :for-macro 'clojure.core/or
    :if-inside-macroexpansion-of #{'clojure.core.async/go}
    :reason "https://github.com/jonase/eastwood/issues/411. Note that `alt!` is not caught as a macro, possibly because `go` is a peculiar macro."})

  (disable-warning
   {:linter :suspicious-expression
    :for-macro 'clojure.core/or
    :within-depth 6
    :if-inside-macroexpansion-of #{'clojure.core.async/alt!!}
    :reason "https://github.com/jonase/eastwood/issues/422"})

  (disable-warning
   {:linter :suspicious-expression
    :for-macro 'clojure.core/and
    :if-inside-macroexpansion-of #{'clojure.core.async/go}})

  (doseq [v [true false]]
    (disable-warning
     {:linter :constant-test
      :qualifier v
      :for-macro 'clojure.core/assert
      :if-inside-macroexpansion-of #{'clojure.core.async/go}}))

  (disable-warning
   {:linter :constant-test
    :for-macro 'clojure.test/is
    :if-inside-macroexpansion-of #{'clojure.core.async/go}}))
