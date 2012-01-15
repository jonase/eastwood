(ns eastwood.linters.misc)

;; Naked use
(defn- warn-on-naked-use [use-expr]
  (doseq [s (map :val (:args use-expr))
          :when (symbol? s)]
    (println "Naked use of" (name s) "in" (-> use-expr :env :ns :name))))

(defn- use? [expr]
  (and (= :invoke (:op expr))
       (= :var (-> expr :fexpr :op))
       (= 'use (-> expr :fexpr :var meta :name))))

(defn- find-and-analyze-use-forms [expr]
  (when (use? expr)
    (warn-on-naked-use expr))
  (doseq [child-expr (:children expr)]
    (find-and-analyze-use-forms child-expr)))

(defn naked-use [exprs]
  (doseq [expr exprs]
    (find-and-analyze-use-forms expr)))


;; Missplaced docstring

(defn- check-def [exp]
  (when (= :fn-expr (-> exp :init :op))
    (doseq [method (-> exp :init :methods)]
      (let [body (:body method)]
        (when (and (= :do (:op body))
                   (< 1 (count (-> body :exprs))))
          (let [first-exp (-> body :exprs first)]
            (when (and (= :literal (:op first-exp))
                       (string? (:val first-exp)))
              (binding [*out* *err*]
                (println "Possibly misplaced docstring," (-> exp :var))))))))))

(defn- find-and-check-defs [exp]
  (when (= :def (:op exp))
    (check-def exp))
  (doseq [child-exp (:children exp)]
    (find-and-check-defs child-exp)))

(defn misplaced-docstrings [exprs]
  (doseq [exp exprs]
    (find-and-check-defs exp)))

;; Nondynamic earmuffed var
(defn- earmuffed? [sym]
  (let [s (name sym)]
    (and (< 2 (count s))
         (.startsWith s "*")
         (.endsWith s "*"))))

(defn- check-earmuffed-def [expr]
  (let [v (:var expr)
        s (.sym v)]
    (when (and (earmuffed? s)
               (not (:is-dynamic expr)))
      (println "Should" v "be marked dynamic?"))))

(defn- find-and-check-earmuffed-defs [expr]
  (when (= :def (:op expr))
    (check-earmuffed-def expr))
  (doseq [child-expr (:children expr)]
    (find-and-check-earmuffed-defs child-expr)))

(defn non-dynamic-earmuffs [exprs]
  (doseq [exp exprs]
    (find-and-check-earmuffed-defs exp)))

