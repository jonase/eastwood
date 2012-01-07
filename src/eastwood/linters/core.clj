(ns eastwood.linters.core)

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


;; Unused private vars
(defn- defs [expr]
  (apply concat
         (when (= :def (:op expr)) [(:var expr)])
         (map defs (:children expr))))

(defn- private-defs [expr]
  (filter #(:private (meta %))
          (defs expr)))

(defn- var-count [expr]
  (if (= :var (:op expr))
    {(:var expr) 1}
    (apply merge-with +
           (map var-count (:children expr)))))

(defn- check-usage-of-private-vars [exprs]
  (let [v-count (apply merge-with + (map var-count exprs))]
    (doseq [pvar (mapcat private-defs exprs)]
      (when-not (or (get v-count pvar)
                    (-> pvar meta :macro))
        (println "Private variable" pvar "is defined but never used")))))

(defn unused-private-vars [exprs]
  (check-usage-of-private-vars exprs))

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

;; Reflection
(defn- check-new [exp]
  (when (not (:ctor exp))
    (println "Unresolved constructor" (:class exp) (-> exp :env :ns :name))))

(defn- check-static-method [exp]
  (when (not (:method exp))
    (println "Unresolved static method" (:method-name exp) (:class exp) (-> exp :env :ns :name))))

(defn- check-instance-method [exp]
  (when (not (:method exp))
    (println "Unresolved instance method" (:method-name exp) (:class exp) (-> exp :env :ns :name))))

(defn- check-static-field [exp]
  (when (not (:field exp))
    (println "Unresolved static field" (:field-name exp) (:class exp) (-> exp :env :ns :name))))

(defn- check-instance-field [exp]
  (when (not (:field exp))
    (println "Unresolved instance field" (:field-name exp) (:class exp) (-> exp :env :ns :name))))


(defn- check-for-reflection [exp]
  (condp = (:op exp)
    :new (check-new exp)
    :static-method (check-static-method exp)
    :instance-method (check-instance-method exp)
    :static-field (check-static-field exp)
    :instance-field (check-instance-field exp)
    nil)
  
  (doseq [c (:children exp)]
    (check-for-reflection c)))

(defn reflection [exprs]
  (doseq [exp exprs]
    (check-for-reflection exp)))

