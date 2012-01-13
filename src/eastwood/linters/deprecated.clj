(ns eastwood.linters.deprecated
  (:use [analyze.util :only [expr-seq print-expr]]))

(defmulti deprecated :op)

(defmethod deprecated :var [expr]
  (-> expr :var meta :deprecated))

(defn java-is-deprecated? [obj]
  (->> obj
       .getAnnotations
       (map #(.annotationType %))
       (some #{java.lang.Deprecated})))

(defmethod deprecated :instance-method [expr]
  (->> expr
       :Expr-obj
       .method
       java-is-deprecated?))

(defmethod deprecated :new [expr]
  (->> expr
       :Expr-obj
       .ctor
       java-is-deprecated?))

(defmethod deprecated :default [_]
  false)


(defmulti report-deprecated :op)

(defmethod report-deprecated :var [expr] 
  (printf "%s is deprecated\n" (:var expr)))

(defmethod report-deprecated :instance-method [expr] 
  (printf "Instance method \"%s\" is deprecated\n" (-> expr :Expr-obj .method)))

(defmethod report-deprecated :new [expr]
  (printf "Constructor \"%s\" is deprecated\n" (-> expr :Expr-obj .ctor)))

(defn deprecations [exprs]
  (doseq [expr exprs
          dexpr (filter deprecated (expr-seq expr))]
    (report-deprecated dexpr)))

