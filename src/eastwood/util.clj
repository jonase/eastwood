(ns eastwood.util
  (:require [clojure.tools.analyzer.ast :as ast]
            [clojure.tools.reader :as trdr]
            [clojure.tools.reader.reader-types :as rdr-types]))

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))

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
  (ast/prewalk ast enhance-extend-invocations-prewalk))

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
  the returned forms, but sometimes this is what you want.

  Hack alert: In order to be able to correctly interpret keywords of
  the form ::name or ::ns-or-ns-alias/name, you should pass in a
  namespace to bind to the *ns* var during this function's reading of
  s.  The assumption is that this namespace and those it requires are
  already loaded before this function is called, and any aliases used
  by keywords are already set up.  It also assumes that the entire
  string is in that same namespace.  Fortunately, this is pretty
  common for most Clojure code as written today."
  [s ns]
  (binding [*ns* (or ns *ns*)]
    (let [rdr (rdr-types/string-push-back-reader s)
          eof (reify)]
      (loop [forms []]
        (let [x (trdr/read rdr nil eof)]
          (if (identical? x eof)
            forms
            (recur (conj forms x))))))))
