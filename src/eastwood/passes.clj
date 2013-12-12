(ns eastwood.passes
  (:refer-clojure :exclude [get-method]))

(defmulti reflect-validated :op)

(defn get-ctor [ast]
  (.getConstructor ^Class (:class ast)
                   (into-array Class (mapv :tag (:args ast)))))

(defn get-field [ast]
  (.getField ^Class (:class ast)
             (name (:field ast))))

(defn get-method [ast]
  (.getMethod ^Class (:class ast)
              (name (:method ast))
              (into-array Class (mapv :tag (:args ast)))))

(defmethod reflect-validated :default [ast] ast)

(defmethod reflect-validated :new [ast]
  (if (:validated? ast)
    (assoc ast :reflected-ctor (get-ctor ast))
    ast))

(defmethod reflect-validated :instance-field [ast]
  (assoc ast :reflected-field (get-field ast)))

(defmethod reflect-validated :instance-call [ast]
  (if (:validated? ast)
    (assoc ast :reflected-method (get-method ast))
    ast))

(defmethod reflect-validated :static-field [ast]
  (assoc ast :reflected-field (get-field ast)))

(defmethod reflect-validated :static-call [ast]
  (if (:validated? ast)
    (assoc ast :reflected-method (get-method ast))
    ast))
