;; This file is not used by Eastwood yet.  It is planned to become a
;; way for users to disable particular warnings based upon which
;; macroexpansions occurred to create the AST node that is the source
;; of the warning.  This will allow disabling warnings that are caused
;; by macros in libraries that the developer does not wish to edit,
;; e.g. because they are a widely used library and they do not wish to
;; create a custom version of it that disables the warnings in some
;; other way.


;; This file is *evaluated* by Eastwood before it starts linting, not
;; merely read.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Notes for warnings to disable in clojure.core, Clojure 1.6.0
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-first}
  :within-depth 4
  :reason "when-first with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-let}
  :within-depth 3
  :reason "when-let with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-some}
  :within-depth 2
  :reason "when-some with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Notes for warnings to disable in library core.match, version 0.2.1
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This is probably a sloppy way to disable some
;; :suspicious-expression warnings.  Consider fine-tuning it further.

(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/and
  :if-inside-macroexpansion-of #{'clojure.core.match/match}
  :within-depth 7
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
;; Notes for warnings to disable in library Carmine, version 2.7.1
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
  :within-depth 3
  :reason "Remove this disable-warning after updating to latest version of Carmine, which has a fix for a bug in a Carmine macro that causes these warnings."})

(disable-warning
 {:linter :constant-test
  :if-inside-macroexpansion-of #{'taoensso.carmine.commands/defcommand}
  :within-depth 4
  :reason "Carmine's defcommand macro commonly expands to contain an if-not with a condition that is a constant."})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Notes for warnings to disable in Korma library, version 0.4.0
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
  :within-depth 2
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
