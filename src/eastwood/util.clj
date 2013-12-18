(ns eastwood.util
  (:require [clojure.tools.analyzer.passes :as pass]
            [clojure.tools.reader :as trdr]
            [clojure.tools.reader.reader-types :as rdr-types]))

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))

(defn ast-nodes [ast]
  (lazy-seq
   (cons ast (mapcat ast-nodes (pass/children ast)))))

(defn enhance-extend-args [extend-args]
  (let [[atype-ast & proto+mmaps-asts] extend-args
        atype-sym (:form atype-ast)]
    (vec
     (cons atype-ast
           (apply concat
                  (for [[proto-ast mmap-ast] (partition 2 proto+mmaps-asts)]
                    (let [proto-sym (:form proto-ast)
                          enh-mmap-vals
                          (vec
                           (map (fn [meth-keyword-ast meth-fn-ast]
                                  (let [meth-keyword (:form meth-keyword-ast)
                                        meth-sym (name meth-keyword)]
                                    (update-in meth-fn-ast
                                               [:env :name]
                                               (constantly
                                                (format
                                                 "protocol %s type %s method %s"
                                                 proto-sym
                                                 (cond (nil? atype-sym) "nil"
                                                       (class? atype-sym) (.getName ^Class atype-sym)
                                                       :else atype-sym)
                                                 meth-sym)))))
                                (:keys mmap-ast) (:vals mmap-ast)))]
                      [proto-ast (assoc mmap-ast :vals enh-mmap-vals)])))))))

(defn extend-invocation-ast? [ast]
  (and (= (:op ast) :invoke)
       (= #'clojure.core/extend (get-in ast [:fn :var]))))

(defn enhance-extend-invocations-prewalk [ast]
  (if (extend-invocation-ast? ast)
    (update-in ast [:args] enhance-extend-args)
    ;; else no enhancement
    ast))

(defn enhance-extend-invocations [ast]
  (pass/prewalk ast enhance-extend-invocations-prewalk))

(defn interface? [obj]
  (and (= Class (class obj))
       (.isInterface ^Class obj)))

(defn invoke-expr? [ast-node]
  (and (= :invoke (:op ast-node))
       (contains? ast-node :fn)
       (contains? (:fn ast-node) :var)))

(defn static-call? [ast-node]
  (and (= :static-call (:op ast-node))
       (contains? ast-node :class)
       (contains? ast-node :method)))

(defn string->forms
  "Treat a string as a sequence of 0 or more Clojure forms, and read
  all of them.  No line or column number metadata will be attached to
  the returned forms, but sometimes this is what you want."
  [s]
  (let [rdr (rdr-types/string-push-back-reader s)
        eof (reify)]
    (loop [forms []]
      (let [x (trdr/read rdr nil eof)]
        (if (identical? x eof)
          forms
          (recur (conj forms x)))))))
