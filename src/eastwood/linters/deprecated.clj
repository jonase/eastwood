(ns eastwood.linters.deprecated)

(defn- var-seq [expr]
  (if (= :var (:op expr))
    [(:var expr)]
    (mapcat var-seq (:children expr))))

(defn- deprecated-var-seq [expr]
  (filter #(:deprecated (meta %)) (var-seq expr)))

(defn deprecated-vars [exprs]
  (doseq [expr exprs
          dvar (deprecated-var-seq expr)]
    (println dvar "is deprecated")))

