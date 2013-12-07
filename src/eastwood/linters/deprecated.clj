(ns eastwood.linters.deprecated
  (:refer-clojure :exclude [get-method])
  (:require [eastwood.passes] 
            [eastwood.util :as util]
            [clojure.tools.analyzer.passes :as passes]))

(defmulti deprecated :op)

(defmethod deprecated :default [_] false)

(defmethod deprecated :var [ast]
  (-> ast :var meta :deprecated))

(defmethod deprecated :new [ast]
  (when-let [ctor (:reflected-ctor ast)] 
    (.isAnnotationPresent ctor java.lang.Deprecated)))

(defmethod deprecated :instance-field [ast]
  (.isAnnotationPresent (:reflected-field ast) java.lang.Deprecated))

(defmethod deprecated :instance-call [ast]
  (when-let [method (:reflected-method ast)] 
    (.isAnnotationPresent method java.lang.Deprecated)))

(defmethod deprecated :static-field [ast]
  (.isAnnotationPresent (:reflected-field ast) java.lang.Deprecated))

(defmethod deprecated :static-call [ast]
  (when-let [method (:reflected-method ast)] 
    (.isAnnotationPresent method java.lang.Deprecated)))

(defmulti msg :op)

(defmethod msg :var [expr] 
  (format "Var '%s' is deprecated." (:var expr)))

(defmethod msg :new [expr]
  (format "Constructor '%s' is deprecated." (:reflected-ctor expr)))

(defmethod msg :instance-call [expr] 
  (format "Instance method '%s' is deprecated." (:reflected-method expr)))

(defmethod msg :instance-field [expr] 
  (format "Instance field '%s' is deprecated." (:reflected-field expr)))

(defmethod msg :static-call [expr]
  (format "Static method '%s' is deprecated." (:reflected-method expr)))

(defmethod msg :static-field [expr]
  (format "Static field '%s' is deprecated." (:reflected-field expr)))

(defn deprecations [{:keys [asts]}]
  (for [ast (map #(passes/postwalk % eastwood.passes/reflect-validated) asts)
        dexpr (filter deprecated (util/ast-nodes ast))]
    {:linter :deprecations
     :msg (msg dexpr)
     :line (-> dexpr :env :line)}))
 