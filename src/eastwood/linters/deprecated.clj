;; TODO: Check for deprecated Interfaces, Classes and Exceptions?

(ns eastwood.linters.deprecated
  (:use [analyze.util :only [expr-seq print-expr]]))

(defmulti deprecated :op)

(defmethod deprecated :var [expr]
  (-> expr :var meta :deprecated))

(defmethod deprecated :instance-method [expr]
  (let [method (-> expr
                   :Expr-obj
                   .method)]
    (when method
      (.isAnnotationPresent method java.lang.Deprecated))))

(defmethod deprecated :static-field [expr]
  (let [field (-> expr
                  :Expr-obj
                  .field)]
    (when field
      (.isAnnotationPresent field java.lang.Deprecated))))

(defmethod deprecated :new [expr]
  (-> expr
      :Expr-obj
      .ctor
      (.isAnnotationPresent java.lang.Deprecated)))

(defmethod deprecated :default [_] false)

(defmulti report-deprecated :op)

(defmethod report-deprecated :var [expr] 
  (printf "Var '%s' is deprecated.\n"
          (:var expr)))

(defmethod report-deprecated :instance-method [expr] 
  (printf "Instance method '%s' is deprecated.\n"
          (-> expr :Expr-obj .method)))

(defmethod report-deprecated :static-field [expr]
  (printf "Static field '%s' is deprecated.\n"
          (-> expr :Expr-obj .field)))

(defmethod report-deprecated :new [expr]
  (printf "Constructor '%s' is deprecated.\n"
          (-> expr :Expr-obj .ctor)))

(defn deprecations [exprs]
  (doseq [expr exprs
          dexpr (filter deprecated (expr-seq expr))]
    (report-deprecated dexpr)))

