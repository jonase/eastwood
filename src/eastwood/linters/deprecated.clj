(ns eastwood.linters.deprecated
  (:refer-clojure :exclude [get-method])
  (:require [eastwood.passes :as pass]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast])
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

(defmulti deprecated-var :op)

(defmethod deprecated-var :var [expr]
  [(:var expr) "Var"])

(defmethod deprecated-var :new [expr]
  [(:reflected-ctor expr) "Constructor"])

(defmethod deprecated-var :instance-call [expr]
  [(:reflected-method expr) "Instance method"])

(defmethod deprecated-var :instance-field [expr]
  [(:reflected-field expr) "Instance field"])

(defmethod deprecated-var :static-call [expr]
  [(:reflected-method expr) "Static method"])

(defmethod deprecated-var :static-field [expr]
  [(:reflected-field expr) "Static field"])

(defn msg [expr]
  (let [[var type] (deprecated-var expr)]
    (format "%s '%s' is deprecated." type var)))

(defn allow-warning [w opt]
  (when-let [regexes (get-in opt [:warning-enable-config :deprecations :symbol-matches])]
    (let [offending-var (-> w :var first str)]
      (some #(re-matches % offending-var) regexes))))

(defn deprecations [{:keys [asts]} opt]
  (for [ast (map #(ast/postwalk % pass/reflect-validated) asts)
        dexpr (filter deprecated (ast/nodes ast))
        :let [loc (pass/code-loc (pass/nearest-ast-with-loc dexpr))
              w {:loc loc
                 :linter :deprecations
                 :msg (msg dexpr)
                 :var (deprecated-var dexpr)}
              allow? (not (allow-warning w opt))]
        :when allow?]
    w
))
