(ns eastwood.linters.misc
  (:use analyze.util))

;; Naked use
(defn- report-on-naked-use [use-expr]
  (doseq [s (map :val (:args use-expr))
          :when (symbol? s)]
    (println "Naked use of" (name s) "in" (-> use-expr :env :ns :name))))

(defn- use? [expr]
  (and (= :invoke (:op expr))
       (= :var (-> expr :fexpr :op))
       (= 'use (-> expr :fexpr :var meta :name))))

(defn naked-use [exprs]
  (doseq [expr (mapcat expr-seq exprs)]
    (when (use? expr)
      (report-on-naked-use expr))))



;; Missplaced docstring

(defn- check-def [expr]
  (when (= :fn-expr (-> expr :init :op))
    (doseq [method (-> expr :init :methods)]
      (let [body (:body method)]
        (when (and (= :do (:op body))
                   (< 1 (count (-> body :exprs))))
          (let [first-exp (-> body :exprs first)]
            (when (and (= :literal (:op first-exp))
                       (string? (:val first-exp)))
              (println "Possibly misplaced docstring," (-> expr :var)))))))))

(defn misplaced-docstrings [exprs]
  (doseq [expr (mapcat expr-seq exprs)
          :when (= (:op expr) :def)]
    (check-def expr)))

;; Nondynamic earmuffed var
(defn- earmuffed? [sym]
  (let [s (name sym)]
    (and (< 2 (count s))
         (.startsWith s "*")
         (.endsWith s "*"))))

(defn- report-earmuffed-def [expr]
  (let [v (:var expr)
        s (.sym v)]
    (when (and (earmuffed? s)
               (not (:is-dynamic expr)))
      (println "Should" v "be marked dynamic?"))))

(defn non-dynamic-earmuffs [exprs]
  (doseq [expr (mapcat expr-seq exprs)
          :when (= (:op expr) :def)]
    (report-earmuffed-def expr)))

;; Def-in-def

(defn def-in-def [exprs]
  (doseq [expr (mapcat expr-seq exprs)
          :when (and (= (:op expr) :def)
                     (-> expr :var meta :macro not))]
    (when (some #(= (:op %) :def) (rest (expr-seq expr)))
      (println "There is a def inside" (:var expr)))))


