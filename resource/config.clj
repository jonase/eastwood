;; NOTE: This file is *evaluated* by Eastwood before it starts
;; linting, not merely read.  This has the advantage that you can use
;; functions, loops, etc.  This can be useful to disable many similar
;; kinds of warnings.

;; This file contains some configuration for disabling warnings that
;; Eastwood uses by default.  Users may write files similar to this
;; one that provide additional configuration.  See the documentation
;; for the :config-files option to Eastwood.

;; The kind of configuration implemented depends upon the linter
;; involved.

;; For :suspicious-expression and :constant-test linters, you can
;; disable them based on which macroexpansions occurred to create the
;; AST node that is the source of the warning.  Thus a developer may
;; disable warnings that are caused by macros in libraries that they
;; do not wish to edit, e.g. because the library is widely used and
;; they do not wish to create a custom version of it that disables the
;; warnings in some other way.

;; For the :wrong-arity linter, you can specify the :arglists to use
;; for a function that is giving incorrect warnings, and Eastwood will
;; check the calls against that :arglists, not the one in the metadata
;; of the function var.


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
  :within-depth 4
  :reason "when-let with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-some}
  :within-depth 3
  :reason "when-some with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

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
;; Configs to disable warnings in Carmine, version 2.7.1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Without a change in Carmine's source code, there is a warning about
;; a clojure.core/let with an empty body every time macro
;; enqueue-request is invoked.  I have filed an issue with Carmine on
;; GitHub, since it appears like it might be a bug in Carmine.  Until
;; that is resolved (and maybe afterwards), it would be good to
;; suppress what can otherwise be a huge number of warnings when
;; linting Carmine itself.

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'taoensso.carmine.commands/enqueue-request}
  :within-depth 4
  :reason "Remove this disable-warning after updating to latest version of Carmine, which has a fix for a bug in a Carmine macro that causes these warnings."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'taoensso.carmine.commands/defcommand}
  :within-depth 5
  :reason "Carmine's defcommand macro commonly expands to contain an if-not with a condition that is a constant."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'taoensso.encore/defalias}
  :within-depth 5
  :reason "Encore's defalias macro commonly expands to contain a when-let with a symbol bound to nil, if no (optional) doc string is given to defalias."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'taoensso.timbre/logf 'taoensso.timbre/log}
  :within-depth 11
  :reason "Timbre's logf and log macros commonly expand to contain a when with condition of (not= file \"NO_SOURCE_PATH\"), which is constant if file is a compile-time constant."})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in Korma, version 0.4.0
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The definition of macro make-query-then-exec is:

;; (defn- make-query-then-exec [query-fn-var body & args]
;;   `(let [query# (-> (~query-fn-var ~@args)
;;                     ~@body)]
;;      (exec query#)))

;; Later many other Korma macros are defined using
;; make-query-then-exec, such as update:

;; (defmacro select
;;   [ent & body]
;;   (make-query-then-exec #'select* body ent))

;; The definitions of these macros are identical to select, except for
;; the var given as the first argument:

;; update, delete, insert, union, union-all, intersect, 

;; defentity has -> in its own definition, where if the body is empty
;; it is a 'trivial' (-> x) form that would normally be warned about.

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/->
  :if-inside-macroexpansion-of #{'korma.core/select 'korma.core/update
                                 'korma.core/delete 'korma.core/insert
                                 'korma.core/union 'korma.core/union-all
                                 'korma.core/intersect
                                 'korma.core/defentity}
  :within-depth 3
  :reason "Many korma.core macros such as defentity, select, update, etc. macro expand to contain expressions of the form (-> expr), which is normal and thus preferable not to be warned about."})

;; Korma macros with and with-batch are similar in having a -> in
;; their macro expansions that are warned about if the body is empty.

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/->
  :if-inside-macroexpansion-of #{'korma.core/with 'korma.core/with-batch}
  :within-depth 4
  :reason "korma.core/with and with-batch macros expand to contain expressions of the form (-> expr), which is normal and thus preferable not to be warned about."})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in hiccup, version 1.0.5
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'hiccup.core/html}
  :within-depth 4
  :reason "hiccup.core/html macro expansions often contain test expressions that are always true or always false."})

(doseq [[fns arglist] '([[hiccup.form/hidden-field
                          hiccup.form/text-field
                          hiccup.form/password-field
                          hiccup.form/email-field
                          hiccup.form/text-area]
                         ([name]
                          [name value])]

                        [[hiccup.form/check-box
                          hiccup.form/radio-button]
                         ([group]
                          [group checked?]
                          [group checked? value])]

                        [[hiccup.form/select-options]
                         ([coll]
                          [coll selected])]

                        [[hiccup.form/drop-down]
                         ([name options]
                          [name options selected])]

                        [[hiccup.form/file-upload]
                         ([name])]

                        [[hiccup.form/label]
                         ([name text])]

                        [[hiccup.form/submit-button
                          hiccup.form/reset-button]
                         ([text])]

                        [[hiccup.form/form-to]
                         ([[method action] & body])]

                        [[hiccup.element/link-to]
                         ([url & content])]

                        [[hiccup.element/mail-to]
                         ([e-mail & [content]])]

                        [[hiccup.element/unordered-list
                          hiccup.element/ordered-list]
                         ([coll])]

                        [[hiccup.element/image]
                         ([src]
                          [src alt])]

                        [[hiccup.test.def/one-form-two-args]
                         ([a b])]

                        [[hiccup.test.def/three-forms]
                         ([] [a] [a b])]

                        [[hiccup.test.def/recursive]
                         ([a])]

                        [[hiccup.test.def/with-map]
                         ([] [a b])]

                        [[hiccup.test.def/three-forms-extra]
                         ([] [a] [a b])]
                       )]
  ;; hiccup defelem's allow an optional first map argument
  (let [defelem-modified-arglists (concat arglist
                                          (map #(vec (cons 'attr-map? %))
                                               arglist))]
    (doseq [f fns]
      (disable-warning
       {:linter :wrong-arity
        :function-symbol f
        :arglists-for-linting defelem-modified-arglists
        :reason (format "%s uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that." f)}))))


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
;; Configs to disable warnings in instaparse, version 1.3.4
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :unused-ret-vals
  :for-value nil
  :if-inside-macroexpansion-of #{'instaparse.gll/dprintln
                                 'instaparse.gll/debug
                                 'instaparse.gll/dpprint}
  :within-depth 1
  :reason "Instaparse macros debug, dprintln, and dpprint macroexpand to nil if debugging/printing are disabled."})

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in potemkin 0.3.11
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of #{'potemkin/def-derived-map}
  :within-depth 2
  :reason "potemkin/def-derived-map expands to code with multiple def's for the same Var."})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in Schema 0.3.3
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of #{'schema.core/defrecord}
  :within-depth 2
  :reason "schema.core/defrecord expands to code with multiple def's for the same Var."})

(disable-warning
 {:linter :unused-ret-vals
  :for-value nil
  :if-inside-macroexpansion-of #{'schema.core/defrecord}
  :within-depth 2
  :reason "schema.core/defrecord often macro expands to code containing an unused nil value."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/loop
  :if-inside-macroexpansion-of #{'schema.core/fn}
  :within-depth 7
  :reason "schema.core/fn macro expansions sometimes contain a loop with an empty body."})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Just for Eastwood testing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'testcases.f01/fn-with-arglists-meta
  :arglists-for-linting
  '([x y z])
  :reason "Only for Eastwood testing"})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in Midje, version 3.0-alpha4
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'midje.sweet/has-suffix
  :arglists-for-linting
  '([expected-suffix]
    [expected-suffix looseness?])
  :reason "midje.sweet/has-suffix takes an optional extra argument which is implied by ? after looseness, but its :arglists metadta does not have a 1-arg arity, which this config adds."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in congomongo, version 0.4.1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :wrong-arity
  :function-symbol 'somnium.congomongo/insert!
  :arglists-for-linting
  '([coll obj & {:keys [from to many write-concern]
                 :or {from :clojure to :clojure many false}}])
  :reason "somnium.congomongo/insert!  uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that."})
