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

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'taoensso.timbre.profiling/defnp 'taoensso.timbre.profiling/p 'taoensso.timbre.profiling/profile}
  :within-depth 11
  :reason "Timbre's defnp, profile and p macros commonly expand to contain a check if the function name is a keyword, which is constant if file is a compile-time constant."})

(disable-warning
 {:linter :constant-test
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'taoensso.carmine/with-new-pubsub-listener}
  :within-depth 8
  :reason "Carmine's with-new-pubsub-listener macro commonly expands to contain an false positive."})

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
                         ([] [a] [a b])])]
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
;; Configs to disable warnings in potemkin 0.3.11
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :redefd-vars
  :if-inside-macroexpansion-of #{'potemkin/def-derived-map}
  :within-depth 2
  :reason "potemkin/def-derived-map expands to code with multiple def's for the same Var."})

(disable-warning
 {:linter :unused-ret-vals
  :if-inside-macroexpansion-of #{'potemkin/import-vars
                                 'potemkin.namespaces/import-vars}
  :within-depth 3
  :reason "Potemkin ipmort-vars often causes :unused-ret-vals warnings for imported Vars."})

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

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'schema.core/defn
                                 'schema.core/fn
                                 'schema.core/defmethod}
  :within-depth 9
  :reason "schema.core/defn, fn, and defmethod always give a constant-test warning for a test 'true' in macro expansions, if it has metadata ^:always-validate.  This config will disable such warnings regardless of the presence of ^:always-validate"})


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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configs to disable warnings in slingshot, version 0.12.2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :suspicious-expression
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'slingshot.slingshot/try+}
  :within-depth 15
  :reason "slingshot.slingshot/try+ generates an (and ...) with one or more arguments. Suppress the 'and called with 1 args' warning."})

(disable-warning
 {:linter :constant-test
  :for-macro 'clojure.core/if
  :if-inside-macroexpansion-of '#{nedap.speced.def/defn
                                  nedap.speced.def/defprotocol
                                  nedap.speced.def/let
                                  nedap.speced.def/letfn
                                  nedap.speced.def/fn}
  :within-depth 13
  :reason "The emitted code emits an (if (class? x) ...) where x may be a class or a protocol. The result cannot be known at compile-time (especially as classes and protocols can be defined in runtime)."})

(disable-warning
 {:linter :wrong-tag
  :if-inside-macroexpansion-of '#{nedap.speced.def/defn
                                  nedap.speced.def/defprotocol
                                  nedap.speced.def/let
                                  nedap.speced.def/letfn
                                  nedap.speced.def/fn}
  :reason "The speced.def library uses an extended meaning for tag syntax."})
