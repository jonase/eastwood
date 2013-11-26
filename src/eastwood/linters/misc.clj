(ns eastwood.linters.misc
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [clojure.tools.analyzer.passes :as pass]))

(def expr-seq identity)

;; Naked use

(defn- use? [expr]
  (and (= :invoke (:op expr))
       (= :var (-> expr :fexpr :op))
       (= 'use (-> expr :fexpr :var meta :name))))

(defn naked-use [exprs]
  (for [expr (mapcat expr-seq exprs)
        :when (use? expr)
        :let [s (filter symbol? (map :val (:args expr)))]
        :when (not-empty s)]
    {:linter :naked-use
     :msg (format "Naked use of %s in %s" (seq s) (-> expr :env :ns :name))
     :line (-> expr :env :line)}))

;; Missplaced docstring

(defn- misplaced-docstring? [expr]
  (when (= :fn-expr (-> expr :init :op))
    (some true?
          (for [method (-> expr :init :methods)
                :let [body (:body method)]
                :when (and (= :do (:op body))
                           (< 1 (count (-> body :exprs))))
                :let [first-expr (-> body :exprs first)]]
            (= :string
               (-> body :exprs first :op))))))

(defn misplaced-docstrings [exprs]
  (for [expr (mapcat expr-seq exprs)
        :when (and (= (:op expr) :def)
                   (misplaced-docstring? expr))]
    {:linter :misplaced-docstrings
     :msg (format "Possibly misplaced docstring, %s" (:var expr))
     :line (-> expr :env :line)}))

;; Nondynamic earmuffed var

(defn- earmuffed? [sym]
  (let [s (name sym)]
    (and (< 2 (count s))
         (.startsWith s "*")
         (.endsWith s "*"))))

(defn non-dynamic-earmuffs [exprs]
  (for [expr (mapcat expr-seq exprs)
        :when (= (:op expr) :def)
        :let [^clojure.lang.Var v (:var expr)
              s (.sym v)]
        :when (and (earmuffed? s)
                   (not (:is-dynamic expr)))]
    {:linter :non-dynamic-earmuffs
     :msg (format "%s should be marked dynamic" v)
     :line (-> expr :env :line)}))

;; redef'd vars

;; Attempt to detect any var that is def's multiple times in the same
;; namespace.  This should even catch cases like the following, where
;; a def is inside of a let, do, etc.

;; (def foo 1)
;; (let [x 5]
;;   (def foo (fn [y] (+ x y))))

;; It should also ignore all occurrences of (declare foo), since it is
;; normal to declare a symbol and later def it.

;; It does not count as a redef'd var any var whose def is nested
;; inside of another def.  Those are treated with a separated
;; :def-in-def lint warning.

;; TBD: Uses of defprotocol seem to create multiple :def's for the
;; protocol name.  See if I can figure out how to recognize this
;; situation and not warn about them.  Also, why don't the :def's have
;; line numbers?


;; TBD: defonce-or-defmulti-macro-expansion? is a bit of a hackish way
;; to recognize the macroexpansion of a defonce or defmulti
;; declaration.  Without doing something to recognize these forms, the
;; two occurrences of (def foo ...) in the expansion leads to a
;; :redefd-vars warning.

(defn count-at-most-n+1
  "Return (count s) if it is at most n+1, otherwise return n+1.  Do
this without counting past n+1 elements in s.  Significantly faster
than (count s) if all you care about is whether it has exactly n
items."
  [s n]
  (let [limit (inc n)]
    (loop [c 0
           s (seq s)]
      (if s
        (if (== c limit)
          c
          (recur (inc c) (next s)))
        c))))


(defn count-equals?
  "Verify that a sequential s has exactly n elements, without counting
past n+1 elements in the sequence.  If s is long, this can be
significantly faster than the otherwise equivalent (= (count s) n)"
  [s n]
  (= n (count-at-most-n+1 s n)))


(defn hasroot-expr?
  [form]
  (and (sequential? form)
       (count-equals? form 2)
       (= '.hasRoot (nth form 0))
       [(nth form 1)]))

(defn contains-hasroot-expr?
  [form]
  (or (hasroot-expr? form)
      (and (sequential? form)
           (some contains-hasroot-expr? form))))


(defn def-expr-with-value?
  [form]
  (and (sequential? form)
       (count-equals? form 3)
       (= 'def (nth form 0))
       [(nth form 1) (nth form 2)]))
  

;; TBD: I am sure defonce-or-defmulti-macro-expansion? can be written
;; more clearly and concisely using core.match or core.logic.

;; TBD: Why doesn't macroexpand replace clojure.core/when-not with
;; equivalent if?  I feel like I'm missing something important here.

;; I think there are two related questions:

;; (1) Why doesn't clojure.core/macroexpand-1 expand the nested
;; clojure.core/when-not expression?

;; (2) Why doesn't clojure.tools.analyzer/analyze's macroexpand do so?
;; Is this because I am not calling it with a correct value for the
;; env parameter?

(defn defonce-or-defmulti-macro-expansion?
  "Return false if form is not the same as a macroexpansion of
a (defonce foo val) expression.  If it is, return [foo val]."
  [form]
  (and (sequential? form)
       (= 'let* (first form))
       (count-equals? form 3)
       (let [bindings (nth form 1)
             body (nth form 2)]
         (and (vector? bindings)
              (= 2 (count bindings))
              (sequential? body)
              (count-equals? body 3)
              (= 'clojure.core/when-not (nth body 0))
              (let [symbol-bound (nth bindings 0)
                    val-bound (nth bindings 1)
                    when-not-condition (nth body 1)
                    when-not-body (nth body 2)]
                (symbol? symbol-bound)
                (sequential? val-bound)
                (count-equals? val-bound 2)
                (= 'def (first val-bound))
                (sequential? when-not-body)
                (count-equals? when-not-body 3)
                (= 'def (nth when-not-body 0))
                (let [first-def-sym (nth val-bound 1)
                      second-def-sym (nth when-not-body 1)
                      [hasroot-symbol] (contains-hasroot-expr?
                                        when-not-condition)]
                  (and hasroot-symbol
                       (symbol? hasroot-symbol)
                       (= symbol-bound hasroot-symbol)
                       (symbol? first-def-sym)
                       (symbol? second-def-sym)
                       (= first-def-sym second-def-sym)
                       [first-def-sym (nth when-not-body 2)])))))))


(def ^:dynamic *def-walker-data* 0)


;; TBD: Test a case like this to see what happens:

;; (defonce foo (defonce bar 5))

;; I doubt many people would write code like that, but it would be a
;; good corner test to see how this code handles it.  It would be good
;; if it could be recognized as a :def-in-def warning.

(defn def-walker-pre1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                nested-defs defonce-or-defmulti-match-stack]} *def-walker-data*
        defonce-or-defmulti-expr? (defonce-or-defmulti-macro-expansion?
                                    (:form ast))
        def? (= :def (:op ast))
        declare? (and def? (-> ast :name meta :declared true?))
        nested-def? (and def?
                         (contains? ancestor-op-set :def))
        inside-defonce-or-defmulti-expr? (some vector?
                                               defonce-or-defmulti-match-stack)]
    (set! *def-walker-data*
          (assoc *def-walker-data*
            :ancestor-op-vec (conj ancestor-op-vec (:op ast))
            :ancestor-op-set-stack (conj ancestor-op-set-stack ancestor-op-set)
            :ancestor-op-set (conj ancestor-op-set (:op ast))
            ;; TBD: We want to remember that a var def'd inside of a
            ;; defonce or defmulti was def'd, but only once, not
            ;; multiple times.  Fortunately all macroexpansions of
            ;; defonce and defmulti in Clojure 1.5.1 have exactly one
            ;; (def foo) and one (def foo val) expression.  Pick the
            ;; second one to remember.
            :top-level-defs
            (let [remember-def? (if (and def? (not declare?) (not nested-def?))
                                  (if inside-defonce-or-defmulti-expr?
                                    (def-expr-with-value? (:form ast))
                                    true)
                                  false)]
              (if remember-def?
                (conj top-level-defs ast)
                top-level-defs))
            :nested-defs (if nested-def?
                           (conj nested-defs ast)
                           nested-defs)
            :defonce-or-defmulti-match-stack (conj defonce-or-defmulti-match-stack
                                                   defonce-or-defmulti-expr?))))
  ast)


(defn def-walker-post1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                nested-defs defonce-or-defmulti-match-stack]} *def-walker-data*]
    (set! *def-walker-data*
          (assoc *def-walker-data*
            :ancestor-op-vec (pop ancestor-op-vec)
            :ancestor-op-set-stack (pop ancestor-op-set-stack)
            :ancestor-op-set (peek ancestor-op-set-stack)
            :defonce-or-defmulti-match-stack (pop defonce-or-defmulti-match-stack))))
  ast)


(defn def-walker [ast-seq]
  (binding [*def-walker-data* {:ancestor-op-vec []
                               :ancestor-op-set #{}
                               :ancestor-op-set-stack []
                               :top-level-defs []
                               :nested-defs []
                               :defonce-or-defmulti-match-stack []}]
    (doseq [ast ast-seq]
      (pass/walk ast def-walker-pre1 def-walker-post1)
;;      (println (format "dbg *def-walker-data* %s"
;;                       (class *def-walker-data*)))
;;      (pp/pprint (select-keys *def-walker-data* [:ancestor-op-vec :ancestor-op-set :ancestor-op-set-stack]))
;;      (pp/pprint (map :var (:top-level-defs *def-walker-data*)))
;;      (pp/pprint (map :var (:nested-defs *def-walker-data*)))
      (assert (empty? (:ancestor-op-vec *def-walker-data*)))
      (assert (empty? (:ancestor-op-set *def-walker-data*)))
      (assert (empty? (:ancestor-op-set-stack *def-walker-data*)))
      (assert (empty? (:defonce-or-defmulti-match-stack *def-walker-data*))))
    (select-keys *def-walker-data* [:top-level-defs :nested-defs])))


(defn var-info [var-ast]
  (select-keys var-ast [:var :env]))


(defn- defd-vars [exprs]
  (let [top-level-vars (:top-level-defs (def-walker exprs))]
;;    (println (format "dbg top-level-vars %s"
;;                     (class top-level-vars)))
    (map var-info top-level-vars)))


(defn redefd-vars [exprs]
  (let [defd-vars (defd-vars exprs)
        defd-var-groups (group-by :var defd-vars)
;;        _ (do
;;            (println (format "dbg all (:op :name) keys of exprs=%s"
;;                             (seq (map (juxt :op :name) exprs))))
;;            (println (format "dbg # defd-vars=%d, unique defd-vars=%d"
;;                             (count defd-vars)
;;                             (count defd-var-groups)))
            ;;(println (format " defd-var-freq="))
            ;;(pp/pprint defd-var-groups)
;;            (println (format "All defd-vars with their line numbers:"))
;;            (doseq [{:keys [var env]} defd-vars]
;;              (println (format "    %s LINE=%d COL=%d FILE=%s"
;;                               var (:line env) (:column env) (:file env)))))
        ]
    (for [[defd-var vlist] defd-var-groups
          :when (> (count vlist) 1)]
      {:linter :redefd-vars
       :msg (format "Var %s def'd %d times at lines: %s"
                    defd-var (count vlist)
                    (string/join " "
                                 (map #(get-in % [:env :line]) vlist)))
       :line (-> (second vlist) :env :line)})))


;; Def-in-def

;; TBD: The former implementation of def-in-def only signaled a
;; warnings if the parent def was not a macro.  Should that be done
;; here, too?  Try to find a small example, if so, and add it to
;; tryanalyzer.

(defn- def-in-def-vars [exprs]
  (let [nested-vars (:nested-defs (def-walker exprs))]
    (map var-info nested-vars)))


(defn def-in-def [exprs]
  (let [nested-vars (def-in-def-vars exprs)]
    (for [nested-var nested-vars]
      {:linter :def-in-def
       :msg (format "There is a def of %s nested inside def TBD"
                    (:var nested-var))
       :line (-> nested-var :env :line)})))
