(ns eastwood.linters.deprecated
  (:refer-clojure :exclude [get-method])
  (:require [eastwood.passes :as pass]
            [eastwood.util :as util]
            [clojure.tools.analyzer.ast :as ast])
  (:import (java.lang.reflect Method Constructor Field)))

(defn no-constructor-found [ctor-info]
  (if (map? ctor-info)
    (let [{:keys [arg-types]} ctor-info]
      (println (format "Error: Eastwood found no constructor for class %s taking %d args with types (%s).  This may occur because Eastwood does not yet do type matching in the same way that Clojure does."
                       (.getName ^Class (:class ctor-info))
                       (count arg-types)
                       (pass/arg-type-str arg-types))))))

(defn no-method-found [kind method-info]
  (if (map? method-info)
    (let [{:keys [arg-types]} method-info]
      (println (format "Error: Eastwood found no %s method named %s for class %s taking %d args with types (%s).  This may occur because Eastwood does not yet do type matching in the same way that Clojure does."
                       (name kind)
                       (:method-name method-info)
                       (.getName ^Class (:class method-info))
                       (count arg-types)
                       (pass/arg-type-str arg-types))))))

(defn no-field-found [kind field-info]
  (if (map? field-info)
    (println (format "Error: Eastwood found no %s field for %s with name %s"
                     (name kind)
                     (.getName ^Class (:class field-info))
                     (:field-name field-info)))))

(defmulti deprecated :op)

(defmethod deprecated :default [_] false)

(defmethod deprecated :var [ast]
  (-> ast :var meta :deprecated))

(defmethod deprecated :new [ast]
  (when-let [ctor (:reflected-ctor ast)]
    (if (instance? Constructor ctor)
      (.isAnnotationPresent ^Constructor ctor Deprecated)
      (do (no-constructor-found ctor)
          false))))

(defmethod deprecated :instance-field [ast]
  (when-let [fld (:reflected-field ast)]
    (if (instance? Field fld)
      (.isAnnotationPresent ^Field fld Deprecated)
      (do (no-field-found :instance fld)
          false))))

(defmethod deprecated :instance-call [ast]
  (when-let [method (:reflected-method ast)]
    (if (instance? Method method)
      (.isAnnotationPresent ^Method method Deprecated)
      (do (no-method-found :instance method)
          false))))

(defmethod deprecated :static-field [ast]
  (when-let [fld (:reflected-field ast)]
    (if (instance? Field fld)
      (.isAnnotationPresent ^Field (:reflected-field ast) Deprecated)
      (do (no-field-found :static fld)
          false))))

(defmethod deprecated :static-call [ast]
  (when-let [method (:reflected-method ast)]
    (if (instance? Method method)
      (.isAnnotationPresent ^Method method Deprecated)
      (do (no-method-found :static method)
          false))))

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
  (for [ast (map #(ast/postwalk % pass/reflect-validated) asts)
        dexpr (filter deprecated (ast/nodes ast))
        :let [loc (-> dexpr :env)]]
    {:linter :deprecations
     :msg (msg dexpr)
     :line (-> loc :line)
     :column (-> loc :column)}))
