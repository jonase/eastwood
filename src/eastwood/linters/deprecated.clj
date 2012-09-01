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
  (let [ctor (-> expr
                 :Expr-obj
                 .ctor)]
    (when ctor
      (.isAnnotationPresent ctor java.lang.Deprecated))))

(defmethod deprecated :default [_] false)

(defmulti msg :op)

(defmethod msg :var [expr] 
  (format "Var '%s' is deprecated."
          (:var expr)))

(defmethod msg :instance-method [expr] 
  (format "Instance method '%s' is deprecated."
          (-> expr :Expr-obj .method)))

(defmethod msg :static-field [expr]
  (format "Static field '%s' is deprecated."
          (-> expr :Expr-obj .field)))

(defmethod msg :new [expr]
  (format "Constructor '%s' is deprecated."
          (-> expr :Expr-obj .ctor)))

(defn deprecations [ast-map]
  (for [[namespace exprs] ast-map
        expr exprs
        dexpr (filter deprecated (expr-seq expr))]
    {:linter :deprecations
     :msg (msg dexpr)
     :line (-> dexpr :env :line)
     :ns namespace}))
