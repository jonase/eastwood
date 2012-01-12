(ns eastwood.linters.deprecated
  (:use [analyze.util :only [expr-seq]]))

(defmulti deprecated :op)

(defmethod deprecated :var [expr]
  (-> expr :var meta :deprecated))

(defmethod deprecated :instance-method [expr]
  (->> expr
       :Expr-obj
       .method
       .getAnnotations
       (map #(.annotationType %))
       (some #{java.lang.Deprecated})))

(defmethod deprecated :default [_])

(defmulti report-deprecated :op)

(defmethod report-deprecated :var [expr] 
  (println (:var expr) "is deprecated"))

(defmethod report-deprecated :instance-method [expr] 
  (println "Instance method" (-> expr :method :name)
           "on" (-> expr :method :declaring-class)
           "is deprecated."))

(defn deprecations [exprs]
  (doseq [expr exprs
          dexpr (filter deprecated (expr-seq expr))]
    (report-deprecated dexpr)))

