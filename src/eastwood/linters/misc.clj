(ns eastwood.linters.misc
  (:require [clojure.string :as string]
            [clojure.pprint :as pp]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
            [eastwood.util :as util]))

(defn var-of-ast [ast]
  (-> ast :form second))

;; Unlimited use

;; Any use statement that does not include a keyword :only or :refer
;; that limits the symbols referred from the other namespace is an
;; 'unlimited' use.  The only use args that will be considered "safe"
;; are the ones that have a :only or :refer keyword in them, to limit
;; the symbols that are referred.

;; These are all OK:

;; [clojure.string :only [replace]]
;; [clojure.string :refer [replace]]
;; [clojure.string :as str :refer [replace]]
;; [clojure [xml :only [emit]] [edn :only [read-string]]]

;; These are all unlimited:

;; name.space
;; [name.space]
;; [name space1 space2]
;; [name [space1] space2]
;; [name [space1] [space2]]
;; [name :as alias]
;; [name1 [name2 :as alias2] [name3 :as alias3]]

(defn- use-arg-ok?
  ([arg] (use-arg-ok? arg 0))
  ([arg depth]
     ;; keyword covers things like :reload or :reload-all typically
     ;; put at the end of a use or require
     (or (keyword? arg)
         (and (sequential? arg)
              (>= (count arg) 2)
              (symbol? (first arg))
              (or (and (keyword? (second arg))
                       (let [opt-map (apply hash-map (rest arg))]
                         (or (contains? opt-map :refer)
                             (contains? opt-map :only))))
                  (and (zero? depth)
                       (every? #(use-arg-ok? % 1) (rest arg))))))))

(defn- use? [ast]
  (and (= :invoke (:op ast))
       (= :var (-> ast :fn :op))
       (= #'clojure.core/use (-> ast :fn :var))))

(defn- remove-quote-wrapper [x]
  (if (and (sequential? x)
           (= 'quote (first x)))
    (second x)
    x))

(defn unlimited-use [{:keys [asts]}]
  (for [ast (mapcat ast/nodes asts)
        :when (use? ast)
        :let [use-args (map remove-quote-wrapper (rest (-> ast :form)))
              s (remove use-arg-ok? use-args)]
        :when (seq s)]
    {:linter :unlimited-use
     :msg (format "Unlimited use of %s in %s" (seq s) (-> ast :env :ns))
     :file (-> ast :env :ns meta :file)
     :line (-> ast :env :ns meta :line)
     :column (-> ast :env :ns meta :column)}))

;; Misplaced docstring

(defn- misplaced-docstring? [expr]
  (when (= :fn (-> expr :init :op))
    (some true?
          (for [method (-> expr :init :methods)
                :let [body (:body method)]
                :when (and (= :do (:op body))
                           (>= (count (-> body :statements)) 1))
                :let [first-expr (-> body :statements first)]]
            (string? (-> first-expr :form))))))

(defn misplaced-docstrings [{:keys [asts]}]
  (for [ast (mapcat ast/nodes asts)
        :when (and (= (:op ast) :def)
                   (misplaced-docstring? ast))
        :let [loc (-> ast var-of-ast meta)]]
    {:linter :misplaced-docstrings
     :msg (format "Possibly misplaced docstring, %s" (var-of-ast ast))
     :file (-> loc :file)
     :line (-> loc :line)
     :column (-> loc :column)}))

;; Nondynamic earmuffed var

(defn- earmuffed? [sym]
  (let [s (name sym)]
    (and (< 2 (count s))
         (.startsWith s "*")
         (.endsWith s "*"))))

(defn non-dynamic-earmuffs [{:keys [asts]}]
  (for [expr (mapcat ast/nodes asts)
        :when (= (:op expr) :def)
        :let [^clojure.lang.Var v (:var expr)
              s (.sym v)]
        :when (and (earmuffed? s)
                   (not (:is-dynamic expr)))]
    {:linter :non-dynamic-earmuffs
     :msg (format "%s should be marked dynamic" v)
     :file (-> expr :env :file)
     :line (-> expr :env :line)
     :column (-> expr :env :column)}))

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


;; defonce-or-defmulti-macro-expansion? is a bit of a hackish way to
;; recognize the macroexpansion of a defonce or defmulti declaration.
;; However, without doing something to recognize these forms, the two
;; occurrences of (def foo ...) in the expansion leads to a
;; :redefd-vars warning.

;; I am sure defonce-or-defmulti-macro-expansion? can be written more
;; clearly and concisely using core.match or core.logic.

;; Why doesn't macroexpand replace clojure.core/when-not with
;; equivalent if?  Answer: Because macroexpand is explicitly
;; documented only to expand the outermost macro invocation, if any,
;; and to leave inner macro invocations untouched.

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
                (and (symbol? symbol-bound)
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
                            [first-def-sym (nth when-not-body 2)]))))))))


(def ^:dynamic *def-walker-data* 0)


;; TBD: Test a case like this to see what happens:

;; (defonce foo (defonce bar 5))

;; I doubt many people would write code like that, but it would be a
;; good corner test to see how this code handles it.  It would be good
;; if it could be recognized as a :def-in-def warning.

(defn def-walker-pre1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                ancestor-defs-vec
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
            :ancestor-defs-vec (if def?
                                 (conj ancestor-defs-vec ast)
                                 ancestor-defs-vec)
            ;; We want to remember that a var def'd inside of a
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
                           (conj nested-defs (assoc ast
                                               :eastwood/enclosing-def-ast
                                               (peek ancestor-defs-vec)))
                           nested-defs)
            :defonce-or-defmulti-match-stack (conj defonce-or-defmulti-match-stack
                                                   defonce-or-defmulti-expr?))))
  ast)


(defn def-walker-post1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                ancestor-defs-vec
                nested-defs defonce-or-defmulti-match-stack]} *def-walker-data*]
    (set! *def-walker-data*
          (assoc *def-walker-data*
            :ancestor-op-vec (pop ancestor-op-vec)
            :ancestor-op-set-stack (pop ancestor-op-set-stack)
            :ancestor-op-set (peek ancestor-op-set-stack)
            :ancestor-defs-vec (if (= :def (peek ancestor-op-vec))
                                 (pop ancestor-defs-vec)
                                 ancestor-defs-vec)
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
      (ast/walk ast def-walker-pre1 def-walker-post1)
;;      (println (format "dbg *def-walker-data* %s"
;;                       (class *def-walker-data*)))
;;      (pp/pprint (select-keys *def-walker-data* [:ancestor-op-vec :ancestor-op-set :ancestor-op-set-stack]))
;;      (pp/pprint (map :var (:top-level-defs *def-walker-data*)))
;;      (pp/pprint (map :var (:nested-defs *def-walker-data*)))
      (assert (empty? (:ancestor-op-vec *def-walker-data*)))
      (assert (empty? (:ancestor-op-set *def-walker-data*)))
      (assert (empty? (:ancestor-op-set-stack *def-walker-data*)))
      (assert (empty? (:ancestor-defs-vec *def-walker-data*)))
      (assert (empty? (:defonce-or-defmulti-match-stack *def-walker-data*))))
    (select-keys *def-walker-data* [:top-level-defs :nested-defs])))


(defn- defd-vars [exprs]
  (:top-level-defs (def-walker exprs)))

(defn redefd-var-loc [ast]
  ;; For some macro expansions, their expansions do not have :line and
  ;; :column info in (-> ast var-of-ast meta).  Try another place it
  ;; can sometimes be found.
  (let [loc1 (-> ast var-of-ast meta)]
    (if (-> loc1 :line)
      loc1
      (-> ast :env))))

(defn redefd-var-loc-desc [var-ast]
  (let [loc (redefd-var-loc var-ast)]
    (str (if-let [f (:file loc)]
           (str f ":")
           "")
         (:line loc) ":" (:column loc))))

(defn redefd-vars [{:keys [asts]}]
  (let [defd-var-asts (defd-vars asts)
        defd-var-groups (group-by #(-> % :form second) defd-var-asts)]
    (for [[defd-var-ast ast-list] defd-var-groups
          :when (> (count ast-list) 1)]
      (let [ast2 (second ast-list)
            loc2 (redefd-var-loc ast2)]
        {:linter :redefd-vars
         :msg (format "Var %s def'd %d times at line:col locations: %s"
                      (var-of-ast ast2)
                      (count ast-list)
                      (string/join
                       " "
                       (map redefd-var-loc-desc ast-list)))
         :file (-> loc2 :file)
         :line (-> loc2 :line)
         :column (-> loc2 :column)}))))


;; Def-in-def

;; TBD: The former implementation of def-in-def only signaled a
;; warning if the parent def was not a macro.  Should that be done
;; here, too?  Try to find a small example, if so, and add it to the
;; tests.

(defn- def-in-def-vars [exprs]
  (:nested-defs (def-walker exprs)))


(defn def-in-def [{:keys [asts]}]
  (let [nested-vars (def-in-def-vars asts)]
    (for [nested-var-ast nested-vars
          :let [loc (-> nested-var-ast var-of-ast meta)]]
      {:linter :def-in-def
       :msg (format "There is a def of %s nested inside def %s"
                    (var-of-ast nested-var-ast)
                    (-> nested-var-ast
                        :eastwood/enclosing-def-ast
                        var-of-ast))
       :file (-> loc :file)
       :line (-> loc :line)
       :column (-> loc :column)})))


;; Wrong arity

(defn wrong-arity [{:keys [asts]}]
  (let [exprs (->> asts
                   (mapcat ast/nodes)
                   (filter :maybe-mismatch-arity))]
    (for [expr exprs
          :let [loc (-> expr :fn :form meta)]]
      {:linter :wrong-arity
       :msg (format "Function on var %s called with %s args, but it is only known to take one of the following args: %s"
                    (-> expr :fn :var)
                    (count (-> expr :args))
                    (string/join "  " (-> expr :fn :arglists)))
       :file (-> loc :file)
       :line (-> loc :line)
       :column (-> loc :column)})))

;; Bad :arglists

(defn argvec-kind [argvec]
  (let [n (count argvec)
        variadic? (and (>= n 2)
                       (= '& (argvec (- n 2))))]
    (if variadic?
      ['>= (- n 2)]
      [n])))

(defn cmp-argvec-kinds [kind1 kind2]
  (if (= '>= (first kind1))
    (if (= '>= (first kind2))
      (compare (second kind1) (second kind2))
      1)
    (if (= '>= (first kind2))
      -1
      (compare (first kind1) (first kind2)))))

(defn all-sigs [arglists]
  (->> arglists
       (map argvec-kind)
       (sort cmp-argvec-kinds)
       (mapcat (fn [kind]
                 (if (= '>= (first kind))
                   [(second kind) :or-more]
                   [(first kind)])))
       vec))

;; TBD: Try to make this *not* warn for macros with custom :arglists,
;; but only for non-macro functions.  Perhaps even better, separate
;; linter names for each type of warning.

;; The code as is will warn about this macro in timbre's namespace
;; taoensso.timbre.utils:

;;(defmacro defonce*
;;  "Like `clojure.core/defonce` but supports optional docstring and attributes
;;  map for name symbol."
;;  {:arglists '([name expr])}
;;  [name & sigs]
;;  (let [[name [expr]] (macro/name-with-attributes name sigs)]
;;    `(clojure.core/defonce ~name ~expr)))

;; TBD: This also does not catch fns created via hiccup's defelem
;; macro, because when def'd they are fine, and then later the macro
;; alters the :arglists metadata on the var.  One way to catch that
;; would be to look at the final value of :arglists after eval'ing the
;; entire namespace.

;; Case 2 handles functions created by deftest, which have no
;; :arglists in the metadata of the var they create, but they do have
;; a :test key.
             
;; Case 3 handles at least the following cases, and maybe more that I
;; have not seen examples of yet.
             
;; (def fun1 #(string? %))
;; (def fun2 map)
;; (def fun3 (fn [y] (inc y)))
;; (defn ^Class fun4 "docstring" {:seesaw {:class `Integer}} [x & y] ...)

(defn bad-arglists [{:keys [asts]}]
  (let [def-fn-asts (->> asts
                         (mapcat ast/nodes)
                         (filter (fn [a]
                                   (and (= :def (:op a))
                                        (not (-> a :name meta :declared true?))
                                        (= :fn (-> a :init :op))))))]
    (apply concat
     (for [a def-fn-asts]
       (let [macro? (-> a :var meta :macro)
             fn-arglists (-> a :arglists)
             fn-arglists2 (-> a :init :arglists)
             macro-args? (or (not macro?)
                             (and (every? #(= '(&form &env) (take 2 %)) fn-arglists)
                                  (every? #(= '(&form &env) (take 2 %)) fn-arglists2)))
             meta-arglists (cond (contains? (-> a :meta :val) :arglists)
                                 (-> a :meta :val :arglists)
                                 ;; see case 2 notes above
                                 (and (contains? (-> a :meta) :keys)
                                      (->> (-> a :meta :keys)
                                           (some #(= :test (get % :val)))))
                                 [[]]
                                 ;; see case 3 notes above
                                 :else nil)
             fn-arglists (if (and macro? macro-args?)
                           (map #(subvec % 2) fn-arglists)
                           fn-arglists)
             fn-arglists2 (if (and macro? macro-args?)
                            (map #(subvec % 2) fn-arglists2)
                            fn-arglists2)
             fn-sigs (all-sigs fn-arglists)
             fn-sigs2 (all-sigs fn-arglists2)
             meta-sigs (all-sigs meta-arglists)
             loc (-> a var-of-ast meta)
             _ (when (not= fn-sigs fn-sigs2)
                 (println (format "Eastwood internal error: fn-sigs=%s != fn-sigs2=%s"
                                  fn-sigs2 (seq fn-arglists2))))]
         (if (and (not (nil? meta-arglists))
                  (not= fn-sigs meta-sigs))
           [{:linter :bad-arglists
             :msg (format "%s on var %s defined taking # args %s but :arglists metadata has # args %s"
                          (if macro? "Macro" "Function")
                          (-> a :name)
                          fn-sigs
                          meta-sigs)
             :file (-> loc :file)
             :line (-> loc :line)
             :column (-> loc :column)}]))))))
