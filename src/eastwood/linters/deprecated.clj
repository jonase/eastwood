(ns eastwood.linters.deprecated
  (:require
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.passes :as pass])
  (:import
   (java.lang.reflect Constructor Field Method)))

(defn no-constructor-found [ctor-info]
  (when (map? ctor-info)
    (let [{:keys [arg-types]} ctor-info]
      (println (format "Error: Eastwood found no constructor for class %s taking %d args with types (%s). This may occur because Eastwood does not yet do type matching in the same way that Clojure does."
                       (.getName ^Class (:class ctor-info))
                       (count arg-types)
                       (pass/arg-type-str arg-types))))))

(defn no-method-found [kind method-info]
  (when (map? method-info)
    (let [{:keys [arg-types]} method-info]
      (println (format "Error: Eastwood found no %s method named %s for class %s taking %d args with types (%s). This may occur because Eastwood does not yet do type matching in the same way that Clojure does."
                       (name kind)
                       (:method-name method-info)
                       (.getName ^Class (:class method-info))
                       (count arg-types)
                       (pass/arg-type-str arg-types))))))

(defn no-field-found [kind field-info]
  (when (map? field-info)
    (println (format "Error: Eastwood found no %s field for %s with name %s"
                     (name kind)
                     (.getName ^Class (:class field-info))
                     (:field-name field-info)))))

(defmulti deprecated? (fn [_ {:keys [op]}]
                        op))

(defmethod deprecated? :default [_ _] false)

(defmethod deprecated? :var [current-ns-str ast]
  (let [{:keys [deprecated]
         ns-obj :ns} (-> ast :var meta)]
    (and deprecated
         (if-not ns-obj ;; this piece of metadata should generally be here, but might be dissoced for whatever reason
           true
           (not= current-ns-str
                 (str ns-obj))))))

(defmethod deprecated? :new [_ ast]
  (when-let [ctor (:reflected-ctor ast)]
    (if (instance? Constructor ctor)
      (.isAnnotationPresent ^Constructor ctor Deprecated)
      (do (no-constructor-found ctor)
          false))))

(defmethod deprecated? :instance-field [_ ast]
  (when-let [fld (:reflected-field ast)]
    (if (instance? Field fld)
      (.isAnnotationPresent ^Field fld Deprecated)
      (do (no-field-found :instance fld)
          false))))

(defmethod deprecated? :instance-call [_ ast]
  (when-let [method (:reflected-method ast)]
    (if (instance? Method method)
      (.isAnnotationPresent ^Method method Deprecated)
      (do (no-method-found :instance method)
          false))))

(defmethod deprecated? :static-field [_ ast]
  (when-let [fld (:reflected-field ast)]
    (if (instance? Field fld)
      (.isAnnotationPresent ^Field (:reflected-field ast) Deprecated)
      (do (no-field-found :static fld)
          false))))

(defmethod deprecated? :static-call [_ ast]
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

(defn omit-warning? [w opt]
  (when-let [regexes (get-in opt [:warning-enable-config :deprecations :symbol-matches])]
    (let [offending-var (-> w :var first str)]
      (->> regexes
           (some (fn [re]
                   (re-find re offending-var)))))))

(defn deprecations [{:keys [asts]} opt]
  (for [{ns-sym :eastwood/ns-sym
         :as ast} (map #(ast/postwalk % pass/reflect-validated) asts)
        :let [ns-str (str ns-sym)]
        {:keys [op]
         :as dexpr} (->> ast
                         ast/nodes
                         (filter (partial deprecated? ns-str)))
        :let [loc (pass/code-loc (pass/nearest-ast-with-loc dexpr))
              w {:loc loc
                 :linter :deprecations
                 :kind op
                 :msg (msg dexpr)
                 :var (deprecated-var dexpr)}]
        :when (not (omit-warning? w opt))]
    w))
