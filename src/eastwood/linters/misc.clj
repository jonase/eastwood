(ns eastwood.linters.misc
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as string]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.env :as env]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.utils :refer [dynamic? resolve-sym]]
   [eastwood.copieddeps.dep2.clojure.tools.analyzer.jvm :as jvm]
   [eastwood.util :as util])
  (:import
   (java.io File)))

(defn var-of-ast [ast]
  (-> ast :form second))

;; Unlimited use

;; Any use statement that does not include a keyword :only or :refer
;; that limits the symbols referred from the other namespace is an
;; 'unlimited' use. The only use args that will be considered "safe"
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
  ([arg]
   (use-arg-ok? arg 0))

  ([arg ^long depth]
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
       (= 'clojure.core/use (util/var-to-fqsym (-> ast :fn :var)))))

(defn- remove-quote-wrapper [x]
  (if (and (sequential? x)
           (= 'quote (first x)))
    (second x)
    x))

(defn unlimited-use [{:keys [asts]} _opt]
  (for [ast (mapcat ast/nodes asts)
        :when (use? ast)
        :let [use-args (map remove-quote-wrapper (rest (-> ast :form)))
              s (remove use-arg-ok? use-args)
              ;; Don't warn about unlimited use of clojure.test. It
              ;; is very common, and seems harmless enough to me in
              ;; test code. Note: No need to have separate versions
              ;; of the expressions as vectors, since the lists are
              ;; equal to them. Also, even though it doesn't cause a
              ;; compile time error if we include ['clojure.test] in
              ;; the set below, it does cause a run-time duplicate
              ;; item exception in createWithCheck if we use Eastwood
              ;; to lint itself, after it re-evaluates this source
              ;; file. I don't fully understand that yet.
              s (remove #{'clojure.test '(clojure.test) '(clojure test)} s)]
        :when (seq s)]
    (let [first-bad-use (first s)
          first-bad-use-sym (if (symbol? first-bad-use)
                              first-bad-use
                              (first first-bad-use))
          loc (meta first-bad-use-sym)]
      {:loc loc
       :linter :unlimited-use
       :msg (format "Unlimited use of %s in %s." (seq s) (-> ast :env :ns))})))

;; Misplaced docstring

(defn- misplaced-docstring? [expr]
  (when-let [fn-ast (util/get-fn-in-def expr)]
    (some true?
          (for [method (-> fn-ast :methods)
                :let [body (:body method)]
                :when (and (= :do (:op body))
                           (>= (count (-> body :statements)) 1))
                :let [first-expr (-> body :statements first)]]
            (string? (-> first-expr :form))))))

(defn misplaced-docstrings [{:keys [asts]} _opt]
  (for [ast (mapcat ast/nodes asts)
        :when (and (= (:op ast) :def)
                   (misplaced-docstring? ast))
        :let [loc (-> ast var-of-ast meta)]]
    {:loc loc
     :linter :misplaced-docstrings
     :msg (format "Possibly misplaced docstring, %s." (var-of-ast ast))}))

;; Nondynamic earmuffed var

(defn- earmuffed? [sym]
  (let [s (name sym)]
    (and (< 2 (count s))
         (.startsWith s "*")
         (.endsWith s "*"))))

(defn non-dynamic-earmuffs [{:keys [asts]} _opt]
  (for [expr (mapcat ast/nodes asts)
        :when (= (:op expr) :def)
        :let [^clojure.lang.Var v (:var expr)
              s (.sym v)
              var-ns (-> v .ns str)
              var-name (name s)
              loc (:env expr)
              e? (earmuffed? s)
              d? (dynamic? v)
              earmuffed-non-dynamic? (and e?
                                          (not d?))
              dynamic-non-earmuffed? (and (not earmuffed-non-dynamic?)
                                          (not e?)
                                          d?)]
        :when (or earmuffed-non-dynamic?
                  dynamic-non-earmuffed?)]
    {:loc loc
     :linter :non-dynamic-earmuffs
     :kind (if earmuffed-non-dynamic?
             :earmuffed-non-dynamic
             :dynamic-non-earmuffed)
     :msg (if earmuffed-non-dynamic?
            (format "%s should be marked dynamic." v)
            (format "%s should use the earmuff naming convention: please use #'%s/*%s* instead." v var-ns var-name))}))

;; redef'd vars

;; Attempt to detect any var that is def'd multiple times in the same
;; namespace. This should even catch cases like the following, where
;; a def is inside of a let, do, etc.

;; (def foo 1)
;; (let [x 5]
;;   (def foo (fn [y] (+ x y))))

;; It should also ignore all occurrences of (declare foo), since it is
;; normal to declare a symbol and later def it.

;; It does not count as a redef'd var any var whose def is nested
;; inside of another def. Those are treated with a separated
;; :def-in-def lint warning.

;; TBD: Uses of defprotocol seem to create multiple :def's for the
;; protocol name. See if I can figure out how to recognize this
;; situation and not warn about them.

(def ^:dynamic *def-walker-data* 0)

;; TBD: Test a case like this to see what happens:

;; (defonce foo (defonce bar 5))

;; I doubt many people would write code like that, but it would be a
;; good corner test to see how this code handles it. It would be good
;; if it could be recognized as a :def-in-def warning.

(defn def-walker-pre1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                ancestor-defs-vec nested-defs]} *def-walker-data*
        def? (= :def (:op ast))
        declare? (and def? (-> ast :name meta :declared true?))
        nested-def? (and def?
                         (contains? ancestor-op-set :def))]
    (set! *def-walker-data*
          (assoc *def-walker-data*
                 :ancestor-op-vec (conj ancestor-op-vec (:op ast))
                 :ancestor-op-set-stack (conj ancestor-op-set-stack ancestor-op-set)
                 :ancestor-op-set (conj ancestor-op-set (:op ast))
                 :ancestor-defs-vec (if def?
                                      (conj ancestor-defs-vec ast)
                                      ancestor-defs-vec)
                 :top-level-defs
                 (if (and def? (not declare?) (not nested-def?))
                   (conj top-level-defs ast)
                   top-level-defs)
                 :nested-defs (if nested-def?
                                (conj nested-defs (assoc ast
                                                         :eastwood/enclosing-def-ast
                                                         (peek ancestor-defs-vec)))
                                nested-defs))))
  ast)

(defn def-walker-post1 [ast]
  (let [{:keys [ancestor-op-vec
                ancestor-op-set-stack
                ancestor-defs-vec]} *def-walker-data*]
    (set! *def-walker-data*
          (assoc *def-walker-data*
                 :ancestor-op-vec (pop ancestor-op-vec)
                 :ancestor-op-set-stack (pop ancestor-op-set-stack)
                 :ancestor-op-set (peek ancestor-op-set-stack)
                 :ancestor-defs-vec (if (= :def (peek ancestor-op-vec))
                                      (pop ancestor-defs-vec)
                                      ancestor-defs-vec))))
  ast)

(defn def-walker [ast-seq]
  (binding [*def-walker-data* {:ancestor-op-vec []
                               :ancestor-op-set #{}
                               :ancestor-op-set-stack []
                               :top-level-defs []
                               :nested-defs []}]
    (doseq [ast ast-seq]
      (ast/walk ast def-walker-pre1 def-walker-post1)
      (assert (empty? (:ancestor-op-vec *def-walker-data*)))
      (assert (empty? (:ancestor-op-set *def-walker-data*)))
      (assert (empty? (:ancestor-op-set-stack *def-walker-data*)))
      (assert (empty? (:ancestor-defs-vec *def-walker-data*))))
    (select-keys *def-walker-data* [:top-level-defs :nested-defs])))

(defn- defd-vars [exprs]
  (:top-level-defs (def-walker exprs)))

(defn allow-both-defs? [linter-id def-ast1 def-ast2 all-asts opt]
  (let [lca-path (util/longest-common-prefix
                  (:eastwood/path def-ast1)
                  (:eastwood/path def-ast2))]
    (if (empty? lca-path)
      true
      (let [suppress-conditions (get-in opt [:warning-enable-config
                                             :redefd-vars])
            ;; Don't bother calculating enclosing-macros if there are
            ;; no suppress-conditions to check, to save time.
            [_lca-path lca-ast]
            (when (seq suppress-conditions)
              (let [a (get-in all-asts lca-path)]
                ;; If the lowest common ancestor is a vector, back up
                ;; one step to the parent, which should be an AST.
                (if (vector? a)
                  [(pop lca-path) (get-in all-asts (pop lca-path))]
                  [lca-path a])))
            encl-macros (when (seq suppress-conditions)
                          (util/enclosing-macros lca-ast))
            match (some #(util/meets-suppress-condition linter-id
                                                        encl-macros
                                                        :eastwood/unset
                                                        %
                                                        opt)
                        suppress-conditions)]
        (not match)))))

(defn remove-dup-defs [linter-id asts all-asts opt]
  (loop [ret []
         asts asts]
    (if (seq asts)
      (let [ast (first asts)
            keep-new-ast? (every? #(allow-both-defs? linter-id % ast all-asts opt)
                                  ret)]
        (recur (if keep-new-ast? (conj ret ast) ret)
               (next asts)))
      ret)))

(defn redefd-var-loc [ast]
  ;; For some macro expansions, their expansions do not have :line and
  ;; :column info in (-> ast var-of-ast meta). Try another place it
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

(defn redefd-vars [{:keys [asts]} opt]
  (let [linter-id :redefd-vars
        defd-var-asts (defd-vars asts)
        defd-var-groups (group-by #(-> % :form second) defd-var-asts)
        ;; Remove any def's for Vars that are inside the same macro
        ;; expansion (from Eastwood configuration) as another def for
        ;; the same Var.
        defd-var-groups (into {}
                              (map (fn [[defd-var def-asts]]
                                     [defd-var
                                      (remove-dup-defs linter-id def-asts asts opt)])
                                   defd-var-groups))]
    (for [[_defd-var ast-list] defd-var-groups
          :when (> (count ast-list) 1)
          :let [ast2 (second ast-list)
                loc2 (redefd-var-loc ast2)
                redefd-var (var-of-ast ast2)
                num-defs (count ast-list)
                w {:loc loc2
                   :linter linter-id
                   :msg (format "Var %s def'd %d times at line:col locations: %s."
                                redefd-var num-defs
                                (string/join
                                 " "
                                 (map redefd-var-loc-desc ast-list)))}
                ;; TBD: true is placeholder for some configurable
                ;; method of disabling redefd-var warnings
                ]]
      (do
        (util/debug-warning w nil opt #{}
                            (fn []
                              (println (format "was generated because of the following %d defs"
                                               num-defs))
                              (println (format "paths to ASTs of %d defs for Var %s"
                                               num-defs redefd-var))
                              (doseq [[i ast] (map-indexed vector ast-list)]
                                (println (format "#%d: %s" (inc (long i)) (:eastwood/path ast))))
                              (doseq [[i ast] (map-indexed vector ast-list)]
                                (println (format "enclosing macros for def #%d of %d for Var %s"
                                                 (inc (long i)) num-defs redefd-var))
                                (pprint/pprint (->> (util/enclosing-macros ast)
                                                    (map #(dissoc % :ast :index)))))))
        w))))

;; Def-in-def

;; TBD: The former implementation of def-in-def only signaled a
;; warning if the parent def was not a macro. Should that be done
;; here, too?  Try to find a small example, if so, and add it to the
;; tests.

(defn- def-in-def-vars [exprs]
  (:nested-defs (def-walker exprs)))

(defn def-in-def [{:keys [asts]} _opt]
  (let [nested-vars (def-in-def-vars asts)]
    (for [nested-var-ast nested-vars
          :let [loc (-> nested-var-ast var-of-ast meta)]]
      {:loc loc
       :linter :def-in-def
       :msg (format "There is a def of %s nested inside def %s."
                    (var-of-ast nested-var-ast)
                    (-> nested-var-ast
                        :eastwood/enclosing-def-ast
                        var-of-ast))})))

;; Helpers for wrong arity and bad :arglists

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

(defn signature-union [arglists]
  (let [kinds (group-by #(= '>= (first %))
                        (map argvec-kind arglists))
        ;; If there are multiple 'N args or more', keep only the one
        ;; with smallest N, since it is the most permissive.
        n-or-more (when (seq (get kinds true))
                    (apply min (map second (get kinds true))))
        ;; If there are exact arg counts that are larger than the
        ;; smallest 'N args or more', remove them. Sort any that are
        ;; smaller, dropping duplicates.
        exacts (->> (get kinds false)
                    (map first)
                    (remove #(and n-or-more (>= (long %)
                                                (long n-or-more))))
                    (into (sorted-set)))]
    (vec (concat exacts (and n-or-more [n-or-more :or-more])))))

(defn deconstruct-signature-union [sigs]
  (let [n (count sigs)]
    (if (= :or-more (peek sigs))
      [(subvec sigs 0 (- n 2))
       (sigs (- n 2))]
      ;; else
      [sigs nil])))

(defn more-restrictive-sigs?
  "sigs1 and sigs2 are expected to be return values of
  signature-union, i.e. vectors of non-negative integers, no
  duplicates, sorted in increasing order, optionally having
  an :or-more keyword at the end.

  Return true if sigs1 are more restrictive than sigs2.

  For example [0 2 4 6] is more restrictive than [0 2 3 :or-more]."
  [sigs1 sigs2]
  (let [[exact-sigs1 or-more1] (deconstruct-signature-union sigs1)
        [exact-sigs2 or-more2] (deconstruct-signature-union sigs2)]
    (if or-more2
      (let [or-more2 (long or-more2)]
        (if or-more1
          (let [or-more1 (long or-more1)]
            (if (>= or-more1 or-more2)
              (set/subset? (set (remove #(>= (long %) or-more2) exact-sigs1))
                           (set exact-sigs2))
              (set/subset? (set (concat exact-sigs1 (range or-more1 or-more2)))
                           (set exact-sigs2))))
          (set/subset? (set (remove #(>= (long %) or-more2) exact-sigs1))
                       (set exact-sigs2))))
      (if or-more1
        false
        (set/subset? (set exact-sigs1) (set exact-sigs2))))))

(defn arg-count-compatible-with-arglists [^long arg-count arglists]
  (let [argvec-kinds (map argvec-kind arglists)]
    (some (fn [ak]
            (if (= '>= (first ak))
              (>= arg-count (long (second ak)))
              (= arg-count (long (first ak)))))
          argvec-kinds)))

(defn wrong-arity [{:keys [asts]} opt]
  (let [invoke-asts (->> asts
                         (mapcat ast/nodes)
                         (filter #(and (= :invoke (:op %))
                                       (-> % :fn :arglists))))]
    (for [ast invoke-asts
          :let [args (:args ast)
                func (:fn ast)
                fn-kind (-> func :op)
                [fn-var fn-sym]
                (case fn-kind
                  :var [(-> func :var)
                        (util/var-to-fqsym (-> func :var))]
                  :local [nil (-> func :form)]
                  [nil 'no-name])
                arglists (:arglists func)
                override-arglists (->> opt
                                       :warning-enable-config
                                       :wrong-arity
                                       (filter (fn [{:keys [function-symbol]}]
                                                 (= function-symbol fn-sym)))
                                       (first))
                lint-arglists (or (-> override-arglists
                                      :arglists-for-linting)
                                  arglists)
                loc (-> func :form meta)]
          :when (not (arg-count-compatible-with-arglists (count args)
                                                         lint-arglists))
          :let [w {:loc loc
                   :linter :wrong-arity
                   :wrong-arity {:kind :the-only-kind
                                 :fn-var fn-var
                                 :call-args args
                                 :ast ast}
                   :msg (format "Function on %s %s called with %s args, but it is only known to take one of the following args: %s."
                                (name fn-kind)
                                (if (= :var fn-kind) fn-var fn-sym)
                                (count args)
                                (string/join "  " lint-arglists))}]
          :when (util/allow-warning w opt)]
      (do
        (util/debug-warning w ast opt #{:enclosing-macros}
                            (fn []
                              (println (format "was generated because of a function call on '%s' with %d args"
                                               fn-sym (count args)))
                              (println "arglists from metadata on function var:")
                              (pprint/pprint arglists)
                              (when override-arglists
                                (println "arglists overridden by Eastwood config to the following:")
                                (pprint/pprint lint-arglists)
                                (println "Reason:" (-> override-arglists :reason)))))
        w))))

;; Bad :arglists

;; Function names below are defined in namespace testcases.arglists

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Places on :op :def AST node to find :arglists keyword with various
;; values.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Function fn-with-arglists1, Clojure 1.6.0:

;; where to find '([name expr]):
;; somewhere in :raw-forms
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([name]):
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form name ...}]
;; (get-in ast [:init :methods 0 :params])

;; Function fn-with-arglists1, Clojure 1.8.0-RC1:

;; where to find '([name expr]):
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([name]):

;; (get-in ast [:init :expr :arglists])
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form name ...}]
;; (get-in ast [:init :expr :methods 0 :params])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Function fn-with-arglists3, Clojure 1.6.0:

;; where to find '([x y] [x y z w]):
;; somewhere in :raw-forms
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([a1] [a1 a2 a3]):
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form a1 ...}]
;; (get-in ast [:init :methods 0 :params])

;; where to find :params [{... :form a1 ...} {... a2 ...} {... a3 ...}]
;; (get-in ast [:init :methods 1 :params])

;; Function fn-with-arglists3, Clojure 1.8.0-RC1:

;; where to find '([x y] [x y z w]):
;; somewhere in :raw-forms
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([a1] [a1 a2 a3]):
;; (get-in ast [:init :expr :arglists])
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form a1 ...}]
;; (get-in ast [:init :expr :methods 0 :params])

;; where to find :params [{... :form a1 ...} {... a2 ...} {... a3 ...}]
;; (get-in ast [:init :expr :methods 1 :params])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Function fn-no-arglists3, Clojure 1.6.0:

;; (-> ast :init :op) is :fn

;; where to find '([x y] [x y z w]):
;; NOWHERE in :raw-forms
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([a1] [a1 a2 a3]):
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form a1 ...}]
;; (get-in ast [:init :methods 0 :params])

;; where to find :params [{... :form a1 ...} {... a2 ...} {... a3 ...}]
;; (get-in ast [:init :methods 1 :params])

;; Function fn-no-arglists3, Clojure 1.8.0-RC1:

;; (-> ast :init :op) is :with-meta
;; (-> ast :init :expr :op) is :fn

;; where to find '([a1] [a1 a2 a3]):
;; NOWHERE in :raw-forms
;; (get-in ast [:meta :form :arglists])
;; (get-in ast [:meta :val :arglists])
;; (get-in ast [:arglists])

;; where to find '([a1] [a1 a2 a3]):
;; (get-in ast [:init :expr :arglists])
;; (get-in ast [:init :arglists])

;; where to find :params [{... :form a1 ...}]
;; (get-in ast [:init :expr :methods 0 :params])

;; where to find :params [{... :form a1 ...} {... a2 ...} {... a3 ...}]
;; (get-in ast [:init :expr :methods 1 :params])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TBD: Try to make this *not* warn for macros with custom :arglists,
;; but only for non-macro functions. Perhaps even better, separate
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
;; alters the :arglists metadata on the var. One way to catch that
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

;; tools.analyzer.jvm 0.6.8 introduced extra level of (quote ...)
;; around the value of (-> a :meta :val :arglists) for function ASTs
;; a, over what version 0.6.7 had. I have filed ticket TANAL-117 to
;; point this out, and learn whether this is a bug or not. Until
;; then, work around it if it is seen.

(defn maybe-unwrap-quote [x]
  (if (and (sequential? x)
           (= 'quote (first x)))
    (second x)
    x))

(defn bad-arglists [{:keys [asts]} _opt]
  (let [def-fn-asts (->> asts
                         (mapcat ast/nodes)
                         (filter (fn [a]
                                   (and (= :def (:op a))
                                        (not (-> a :name meta :declared true?))
                                        (or (= :fn (-> a :init :op))
                                            ;; this case handles
                                            ;; Clojure 1.8.0-RC1
                                            (= :fn (-> a :init :expr :op)))))))]
    (apply concat
           (for [a def-fn-asts]
             (let [macro? (-> a :var meta :macro)
                   fn-arglists (-> a :init :arglists)
                   macro-args? (or (not macro?)
                                   (every? #(= '(&form &env) (take 2 %)) fn-arglists))
                   meta-arglists (cond (contains? (-> a :meta :val) :arglists)
                                       (maybe-unwrap-quote
                                        (-> a :meta :val :arglists))
                                       ;; see case 2 notes above
                                       (and (not (-> a :var meta :arglists))
                                            (contains? (-> a :meta) :keys)
                                            (->> (-> a :meta :keys)
                                                 (some #(= :test (get % :val)))))
                                       [[]]
                                       ;; see case 3 notes above
                                       :else nil)
                   fn-arglists (if (and macro? macro-args?)
                                 (map #(subvec % 2) fn-arglists)
                                 fn-arglists)
                   fn-sigs (signature-union fn-arglists)
                   meta-sigs (signature-union meta-arglists)
                   loc (-> a var-of-ast meta)]
               (when (and (not (nil? meta-arglists))
                          (not (more-restrictive-sigs? meta-sigs fn-sigs)))
                 [{:loc loc
                   :linter :bad-arglists
                   :msg (format "%s on var %s defined taking # args %s but :arglists metadata has # args %s."
                                (if macro? "Macro" "Function")
                                (-> a :name)
                                fn-sigs
                                meta-sigs)}]))))))

;; TBD: Consider also looking for local symbols in positions of forms
;; where they appeared to be used as functions, e.g. as the second arg
;; to map, apply, etc.
(defn local-shadows-var [{:keys [asts]} _opt]
  (for [{:keys [op fn env]} (mapcat ast/nodes asts)
        :when (and (= op :invoke)
                   ;; In the examples I have looked at, (:o-tag fn) is
                   ;; also equal to 'clojure.lang.AFunction. What is
                   ;; the difference between that and (:tag fn) ?
                   (not= clojure.lang.AFunction (:tag fn))
                   (contains? (:locals env) (:form fn)))
        :let [v (env/ensure (jvm/global-env) (resolve-sym (:form fn) env))]
        :when v]
    {:loc env
     :linter :local-shadows-var
     :msg (str "local: " (:form fn) " invoked as function shadows var: " v ".")}))

;; Wrong ns form

(def allowed-ns-reference-keywords
  #{:refer-clojure
    :require :use :import
    :load :gen-class})

;; Nearly identical to clojure.core/libspec?

(defn libspec? [x]
  (or (symbol? x)
      (and (vector? x)
           (or (<= (count x) 1)
               (keyword? (second x))))))

(defn symbol-list? [x]
  (and (or (list? x)
           (vector? x))
       (every? symbol? x)))

(defn map-from-symbol-to-symbol? [x]
  (and (map? x)
       (every? symbol? (keys x))
       (every? symbol? (vals x))))

(defn most-specific-loc [loc form]
  (if (contains? (meta form) :line)
    (meta form)
    loc))

;; kw :require can have options:
;; :as symbol
;; :refer symbol-list

;; kw :use can have the above options plus:
;; :exclude symbol-list
;; :only symbol-list
;; :rename map-of-fromsymbol-tosymbol

(defn warnings-for-libspec [libspec kw loc]
  (cond
    (symbol? libspec)
    ;; Clojure 1.6.0 and probably earlier throws an exception for this
    ;; case during eval of require, so having a check for it in Eastwood
    ;; is redundant. Even Eastwood never shows the warning because the
    ;; eval of the form throws an exception, before linting begins.
    ;;   (if-not (nil? (namespace arg))
    ;;     [(util/add-loc-info
    ;;       loc
    ;;       {:linter :wrong-ns-form
    ;;        :msg (format "%s has a symbol libspec with a namespace qualifier: %s"
    ;;                     kw arg)})])
    []

    ;; Clojure 1.6.0 and probably earlier throw an exception during
    ;; eval for at least some cases of a non-symbol being first in the
    ;; libspec, so it might not be possible to make a test hitting this
    ;; case if done after eval.
    (not (symbol? (first libspec)))
    [{:loc loc
      :linter :wrong-ns-form
      :kind :vector-libspec-non-symbol
      :msg (format "%s has a vector libspec that begins with a non-symbol: %s."
                   kw (first libspec))}]

    ;; See above for checking for namespace-qualified symbols naming
    ;; namespaces.
    ;;   (not (nil? (namespace (first libspec))))
    ;;   [{:loc loc :linter :wrong-ns-form
    ;;      :msg (format "%s has a vector libspec beginning with a namespace-qualified symbol: %s"
    ;;                   kw (first libspec))}]

    ;; Some of these checks are already preconditions to calling this
    ;; function from warnings-for-libspec-or-prefix-list, but not if it
    ;; was called to check libspecs in a prefix list.
    (= 1 (count libspec))
    []   ;; nothing more to check

    (even? (count libspec))
    [{:loc loc
      :linter :wrong-ns-form
      :kind :vector-lib-spec-with-even-count
      :msg (format "%s has a vector libspec with an even number of items. It should always be a symbol followed by keyword / value pairs: %s."
                   kw libspec)}]

    :else
    (let [libspec-opts (apply hash-map (rest libspec))
          options (keys libspec-opts)
          allowed-options
          (case kw
            ;; Note: The documentation for require only mentions :as
            ;; and :refer as options. However, Clojure allows and
            ;; correctly handles :exclude and :rename as options in a
            ;; :require libspec, and as long as there is a :refer
            ;; option, they behave correctly as they would for a use
            ;; with those options. :only never makes sense for
            ;; :require, as :refer can be used for that purpose
            ;; instead.
            :require (merge
                      {:as :symbol, :refer :symbol-list-or-all,
                       :include-macros :true, :refer-macros :symbol-list}
                      (if (contains? libspec-opts :refer)
                        {:exclude :symbol-list,
                         :rename :map-from-symbol-to-symbol}
                        {}))
            :require-macros (merge
                             {:as :symbol, :refer :symbol-list-or-all}
                             (if (contains? libspec-opts :refer)
                               {:exclude :symbol-list,
                                :rename :map-from-symbol-to-symbol}
                               {}))
            :use {:as :symbol :refer :symbol-list-or-all,
                  :exclude :symbol-list, :rename :map-from-symbol-to-symbol,
                  :only :symbol-list}
            :use-macros {:as :symbol :refer :symbol-list-or-all,
                         :exclude :symbol-list, :rename :map-from-symbol-to-symbol,
                         :only :symbol-list}
            :refer-clojure {:exclude :symbol-list,
                            :rename :map-from-symbol-to-symbol}
            :import {})
          bad-option-keys (set/difference (set options)
                                          (set (keys allowed-options)))
          libspec-opts (select-keys libspec-opts
                                    (keys allowed-options))
          bad-option-val-map
          (into {}
                (remove (fn [[option-key option-val]]
                          (case (allowed-options option-key)
                            :symbol (symbol? option-val)
                            :symbol-list (symbol-list? option-val)
                            :symbol-list-or-all (or (= :all option-val)
                                                    (symbol-list? option-val))
                            :map-from-symbol-to-symbol
                            (map-from-symbol-to-symbol? option-val)
                            :true (= true option-val)))
                        libspec-opts))]
      (concat
       (if (seq bad-option-keys)
         [{:loc loc
           :linter :wrong-ns-form
           :kind :wrong-option-keys
           :msg (format "%s has a libspec with wrong option keys: %s - option keys for %s should only include the following: %s."
                        kw (string/join " " (sort bad-option-keys))
                        kw (string/join " " (sort (keys allowed-options))))}]
         [])
       (for [[option-key bad-option-val] bad-option-val-map]
         {:loc loc
          :linter :wrong-ns-form
          :kind :bad-option
          :msg (format "%s has a libspec with option key %s that should have a value that is a %s, but instead it is: %s."
                       kw option-key
                       (case (allowed-options option-key)
                         :symbol "symbol"
                         :symbol-list "list of symbols"
                         :symbol-list-or-all "list of symbols, or :all"
                         :map-from-symbol-to-symbol
                         "map from symbols to symbols")
                       bad-option-val)})))))

;; Note: The arg named 'arg' can contain a libspec _or_ a prefix list.

(defn warnings-for-libspec-or-prefix-list [arg kw loc]
  (let [loc (most-specific-loc loc arg)]
    (cond
      ;; Even though a prefix list is called a list in the Clojure
      ;; documentation, it seems to be reasonably common that people
      ;; use vectors for them. Clojure 1.6.0 itself distinguishes
      ;; between libspec or prefix list by considering it a libspec if
      ;; it has at most 1 item, or the second item is a keyword (see
      ;; clojure.core/libspec?). Do the same here.
      (libspec? arg)
      (warnings-for-libspec arg kw loc)

      (or (list? arg) (vector? arg))
      (cond
        ;; This case can occur, with no exception from Clojure. There is a
        ;; test case for it in testcases.wrongnsform
        (and (list? arg) (= 1 (count arg)))
        [{:loc loc
          :linter :wrong-ns-form
          :kind :empty-list
          :msg (format "%s has an arg that is a 1-item list. Clojure silently does nothing with this. To %s it as a namespace, it should be a symbol on its own or it should be inside of a vector, not a list. To use it as the first part of a prefix list, there should be libspecs after it in the list: %s."
                       kw (name kw) arg)}]

        :else
        (mapcat #(warnings-for-libspec % kw loc) (rest arg)))

      :else
      ;; Not sure if there is a test for this case, where Clojure 1.6.0
      ;; will not throw an exception during eval.
      [{:loc loc
        :linter :wrong-ns-form
        :kind :unexpected-value
        :msg (format "%s has an arg that is none of the allowed things of: a keyword, symbol naming a namespace, a libspec (in a vector), a prefix list (in a list or vector): %s."
                     kw arg)}])))

(defn warnings-for-one-ns-form [ns-ast]
  (let [loc (:env ns-ast)
        references (-> ns-ast :raw-forms first nnext)
        [_docstring references] (if (string? (first references))
                                  [(first references) (next references)]
                                  [nil references])
        [_attr-map references] (if (map? (first references))
                                 [(first references) (next references)]
                                 [nil references])
        non-lists (remove list? references)
        references (filter list? references)
        good-kw? #(allowed-ns-reference-keywords (first %))
        wrong-kws (remove good-kw? references)
        references (filter good-kw? references)]
    (concat
     (for [non-list non-lists]
       {:loc (most-specific-loc loc non-list)
        :linter :wrong-ns-form
        :kind :not-a-list
        :msg (format "ns references should be lists. This is not: %s."
                     non-list)})
     (for [wrong-kw wrong-kws]
       {:loc (most-specific-loc loc wrong-kw)
        :linter :wrong-ns-form
        :kind :unexpected-reference
        :msg (format "ns reference starts with '%s' - should be one one of the keywords: %s."
                     (first wrong-kw)
                     (string/join " " (sort allowed-ns-reference-keywords)))})
     (apply concat
            (for [reference references
                  :let [[kw & reference-args] reference]]
              (case kw
                (:require :use)
                (let [flags (set (filter keyword? reference-args))
                      valid-flags #{:reload :reload-all :verbose}
                      invalid-flags (set/difference flags valid-flags)
                      valid-but-unusual-flags (set/intersection flags valid-flags)
                      libspecs-or-prefix-lists (remove keyword? reference-args)]
                  (concat
                   (when (seq invalid-flags)
                     [{:loc (most-specific-loc loc reference)
                       :linter :wrong-ns-form
                       :kind :unknown-flags
                       :msg (format "%s contains unknown flags: %s - flags should only be the following: %s."
                                    kw (string/join " " (sort invalid-flags))
                                    (string/join " " (sort valid-flags)))}])
                   (when (seq valid-but-unusual-flags)
                     [{:loc (most-specific-loc loc reference)
                       :linter :wrong-ns-form
                       :kind :repl-flags
                       :msg (format "%s contains the following valid flags, but it is most common to use them interactively, not in ns forms: %s."
                                    kw (string/join
                                        " " (sort valid-but-unusual-flags)))}])
                   (mapcat #(warnings-for-libspec-or-prefix-list % kw loc)
                           libspecs-or-prefix-lists)))
                :import [] ;; tbd: no checking yet
                :refer-clojure [] ;; tbd: no checking yet
                :gen-class [] ;; tbd: no checking yet
                :load []) ;; tbd: no checking yet
              )))))

(defn wrong-ns-form [{:keys [asts]} _opt]
  (let [ns-asts (util/ns-form-asts asts)
        warnings (mapcat warnings-for-one-ns-form ns-asts)]
    (if (> (count ns-asts) 1)
      (cons {:loc (-> ns-asts second :env)
             :linter :wrong-ns-form
             :kind :duplicate
             :msg "More than one ns form found in same file."}
            warnings)
      warnings)))

(defn make-lint-warning [kw msg cwd file]
  {:kind :lint-warning,
   :warn-data (let [inf (util/file-warn-info file cwd)]
                (merge
                 {:linter kw
                  :msg (format (str msg " '%s'. It will not be linted.")
                               (:uri-or-file-name inf))}
                 inf))})

(defn no-ns-form-found-files [dir-name-strs files filemap linters cwd {:keys [config-files
                                                                              builtin-config-files]}]
  (when (some #{:no-ns-form-found} linters)
    (let [omit (->> config-files
                    (into builtin-config-files)
                    (keep (some-fn io/resource io/file))
                    (map str)
                    (set))
          tfilemap (-> filemap keys set)
          maybe-data-readers (->> dir-name-strs
                                  (map #(File.
                                         (str % File/separator
                                              "data_readers.clj")))
                                  set)]
      {:lint-warnings
       (->> (set/difference files tfilemap maybe-data-readers omit)
            (map (partial make-lint-warning :no-ns-form-found "No ns form was found in file" cwd)))})))

(defn non-clojure-files [non-clojure-files linters cwd]
  (when (some #{:non-clojure-file} linters)
    {:lint-warnings
     (map (partial make-lint-warning :non-clojure-file "Non-Clojure file" cwd) non-clojure-files)}))
