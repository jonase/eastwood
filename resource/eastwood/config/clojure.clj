;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in clojure.core, version 1.6.0
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of #{'clojure.core/defonce 'clojure.core/defmulti}
  :within-depth 2
  :reason "defonce, defmulti expand to code with multiple def's for the same Var."})

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
  :within-depth 7
  :reason "clojure.spec's `coll-of` can contain `clojure.core/or` invocations with only one argument."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.core/cond-> 'clojure.core/cond->>}
  :within-depth 2
  :reason "Allow cond-> and cond->> to have constant tests without warning"})

(disable-warning
  {:linter :constant-test
   :if-inside-macroexpansion-of #{'clojure.core/as->}
   :within-depth 2
   :reason "Allow as-> to have constant tests without warning"})

(disable-warning
  {:linter :wrong-arity
   :function-symbol 'clojure.core/eduction
   :arglists-for-linting '([& xform])
   :reason "eduction takes a sequence of transducer with a collection as the last item"})

