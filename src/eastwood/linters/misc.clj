(ns eastwood.linters.misc
  (:use analyze.util))

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
        :let [v (:var expr)
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
