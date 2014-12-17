;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in core.contracts, version 0.0.5
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of
  #{'clojure.core.contracts.constraints/defconstrainedrecord}
  :within-depth 2
  :reason "defconstrainedrecord expand to code with multiple def's for the same Var."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in core.match, version 0.2.1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This is probably a sloppy way to disable some
;; :suspicious-expression warnings.  Consider fine-tuning it further.

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'clojure.core.match/match}
  :within-depth 13
  :reason "Many clojure.core.match/match macro expansions contain expressions of the form (and expr).  This is normal, and probably simplifies the definition of match."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'clojure.core.match/match}
  ;; suppress :constant-test warnings even if arbitrarily deep inside
  ;; a macroexpansion of the match macro.  I have seen one 22 deep in
  ;; the list, and several 7 or 10 deep.
  :within-depth nil
  :reason "Many clojure.core.match/match macro expansions contain test expressions that are always true or always false.  This is normal, and probably simplifies the definition of match."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in java.jdbc, version 0.3.3
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'clojure.java.jdbc/query
  :arglists-for-linting
  '([db sql-params & {:keys [result-set-fn row-fn identifiers as-arrays?]
                      :or {row-fn identity
                           identifiers str/lower-case}}])
  :reason "clojure.java.jdbc/query uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that."})

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'clojure.java.jdbc/execute!
  :arglists-for-linting
  '([db sql-params & {:keys [transaction? multi?]
                      :or {transaction? true multi? false}}])
  :reason "clojure.java.jdbc/execute! uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that."})

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'clojure.java.jdbc/insert!
  :arglists-for-linting
  '([db table & options])
  :reason "clojure.java.jdbc/insert! uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in core.typed , version
;; core.typed-pom-0.2.72 plus commits up to Dec 1 2014
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(doseq [macro '(clojure.core/case clojure.core/condp clojure.core/and)]
  (disable-warning
   {:linter :suspicious-expression
    :for-macro macro
    :if-inside-macroexpansion-of #{'clojure.core.typed.utils/def-object
                                   'clojure.core.typed.utils/def-type
                                   'clojure.core.typed.utils/def-filter}
    :within-depth nil
    :reason "def-object and def-type macro expansions contain several suspicious-looking macros, including case, condp, and."}))

(disable-warning
 {:linter :unused-ret-vals
  :for-value nil
  :if-inside-macroexpansion-of #{'clojure.core.typed.frees/add-frees-method
                                 'clojure.core.typed.check/add-check-method
                                 'clojure.core.typed.check/add-invoke-special-method
                                 'clojure.core.typed.check/add-invoke-apply-method
                                 'clojure.core.typed.check-cljs/add-check-method
                                 'clojure.core.typed.collect-cljs/add-collect-method}
  :within-depth 4
  :reason "core.typed macro add-{frees,check}-method contain an unused nil in their macroexpansion when there are no pre/post conditions, which is the common case."})

(disable-warning
 {:linter :unused-ret-vals
  :if-inside-macroexpansion-of #{'clojure.core.typed/ann-form}
  :within-depth 1
  :reason "core.typed macro ann-form can expand to its first argument, and is often used in places where its return value is unused.  This seems to be expected normal usage."})

(disable-warning
 {:linter :unused-ret-vals
  :if-inside-macroexpansion-of #{'clojure.core.typed/letfn>}
  :within-depth 2
  :reason "core.typed macro letfn> expands to a letfn with a map as a first body expression, followed by more things in the body.  Not sure why."})

(disable-warning
 {:linter :unused-ret-vals
  :for-value nil
  :if-inside-macroexpansion-of #{'clojure.core.typed.collect-phase/add-collect-method}
  :within-depth 4
  :reason "core.typed macro add-collect-method expands to a fn that often has nil as first expression in body, probably a place-holder for pre/post-conditions.  Not sure why."})

