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

;; Def-in-def

(defn def-in-def [exprs]
  (for [expr (mapcat expr-seq exprs)
        :when (and (= (:op expr) :def)
                   (-> expr :var meta :macro not)
                   (some #(= (:op %) :def) (rest (expr-seq expr))))]
    {:linter :def-in-def
     :msg (format "There is a def inside %s" (:var expr))
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

(def ^:dynamic *def-walker-data* 0)


(defn def-walker-pre1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                nested-defs]} *def-walker-data*
        def? (= :def (:op ast))
        declare? (and def? (-> ast :name meta :declared true?))
        nested-def? (and def?
                         (contains? ancestor-op-set :def))]
    (set! *def-walker-data*
          (assoc *def-walker-data*
            :ancestor-op-vec (conj ancestor-op-vec (:op ast))
            :ancestor-op-set-stack (conj ancestor-op-set-stack ancestor-op-set)
            :ancestor-op-set (conj ancestor-op-set (:op ast))
            :top-level-defs (if (and def? (not declare?) (not nested-def?))
                              (conj top-level-defs ast)
                              top-level-defs)
            :nested-defs (if nested-def?
                           (conj nested-defs ast)
                           nested-defs))))
  ast)


(defn def-walker-post1 [ast]
  (let [{:keys [ancestor-op-vec ancestor-op-set
                ancestor-op-set-stack top-level-defs
                nested-defs]} *def-walker-data*]
    (set! *def-walker-data*
          (assoc *def-walker-data*
            :ancestor-op-vec (pop ancestor-op-vec)
            :ancestor-op-set-stack (pop ancestor-op-set-stack)
            :ancestor-op-set (peek ancestor-op-set-stack))))
  ast)


(defn def-walker [ast-seq]
  (binding [*def-walker-data* {:ancestor-op-vec []
                               :ancestor-op-set #{}
                               :ancestor-op-set-stack []
                               :top-level-defs []
                               :nested-defs []}]
    (doseq [ast ast-seq]
      (pass/walk ast def-walker-pre1 def-walker-post1)
;;      (println (format "dbg *def-walker-data* %s"
;;                       (class *def-walker-data*)))
;;      (pp/pprint (select-keys *def-walker-data* [:ancestor-op-vec :ancestor-op-set :ancestor-op-set-stack]))
;;      (pp/pprint (map :var (:top-level-defs *def-walker-data*)))
;;      (pp/pprint (map :var (:nested-defs *def-walker-data*)))
      (assert (empty? (:ancestor-op-vec *def-walker-data*)))
      (assert (empty? (:ancestor-op-set *def-walker-data*)))
      (assert (empty? (:ancestor-op-set-stack *def-walker-data*))))
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
