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
  ;(println "GOTHERE")
  (->> expr
       :Expr-obj
       .method
       java-is-deprecated?))

#_(defmethod deprecated :new [expr]
  (println "GOTHERE")
  (->> expr
       :Expr-obj
       .ctor
       java-is-deprecated?))

(defmethod deprecated :default [_]
  (println (:op _))
  false)


(defmulti report-deprecated :op)

(defmethod report-deprecated :var [expr] 
  (println (:var expr) "is deprecated"))

(defmethod report-deprecated :instance-method [expr] 
  (println "Instance method" (str \" (-> expr :Expr-obj .method) \")
           "is deprecated."))

(defmethod report-deprecated :new [expr]
  (println "Constructor" (str \" (-> expr :Expr-obj .ctor) \") "is deprecated"))

(defn deprecations [exprs]
  (doseq [expr exprs
          dexpr (filter deprecated (expr-seq expr))]
    (report-deprecated dexpr)))

