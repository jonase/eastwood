(ns eastwood.linters.reflection)

(def expr-seq identity)


(defmulti reflective-call? :op)

(defmethod reflective-call? :instance-method [expr]
  (not (:method expr)))

(defmethod reflective-call? :instance-field [expr]
  (not (:field expr)))

(defmethod reflective-call? :default [_] false)

(defmulti msg :op)

(defmethod msg :instance-method [expr]
  (format "Unresolved instance method %s in %s"
           (:method-name expr)
           (-> expr :env :ns :name)))

(defmethod msg :instance-field [expr]
  (format "Unresolved instance field %s in %s"
           (:field-name expr)
           (-> expr :env :ns :name)))

(defn reflection [exprs]
  (for [expr (mapcat expr-seq exprs)
        :when (reflective-call? expr)]
    {:linter :reflection
     :msg (msg expr)
     :line (-> expr :env :line)}))

