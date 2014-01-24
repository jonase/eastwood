(ns eastwood.util
  (:import [java.io StringReader]
           [clojure.lang LineNumberingPushbackReader])
  (:require [clojure.tools.analyzer.ast :as ast]
            [clojure.tools.reader :as trdr]
            [clojure.pprint :as pp]
            [clojure.tools.reader.reader-types :as rdr-types]))

(defn map-keys [f m]
  (into (empty m)
        (for [[k v] m] [(f k) v])))

(defn map-vals [f m]
  (into (empty m)
        (for [[k v] m] [k (f v)])))

(defn filter-keys [f m]
  (into (empty m)
        (filter (fn [[k _]] (f k)) m)))

(defn filter-vals [f m]
  (into (empty m)
        (filter (fn [[_ v]] (f v)) m)))

(defn pprint-ast-node [ast]
  (binding [*print-meta* true
            ;;*print-level* 12
            *print-length* 50]
    (pp/pprint ast)))

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))

;; walk and prewalk were copied from Clojure 1.6.0-alpha3.  walk
;; includes the case for clojure.lang.IRecord, without which it will
;; throw exceptions when walking forms containing record instances.
(defn walk
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall."
  [inner outer form]
  (cond
   (list? form) (outer (apply list (map inner form)))
   (instance? clojure.lang.IMapEntry form) (outer (vec (map inner form)))
   (seq? form) (outer (doall (map inner form)))
   (instance? clojure.lang.IRecord form)
     (outer (reduce (fn [r x] (conj r (inner x))) form form))
   (coll? form) (outer (into (empty form) (map inner form)))
   :else (outer form)))

(defn prewalk
  "Like postwalk, but does pre-order traversal."
  [f form]
  (walk (partial prewalk f) identity (f form)))

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

(defn regex? [x]
  (instance? java.util.regex.Pattern x))

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
  [s ns include-line-col-metadata?]
  (binding [*ns* (or ns *ns*)]
    (let [rdr (if include-line-col-metadata?
                (LineNumberingPushbackReader. (StringReader. s))
                (rdr-types/string-push-back-reader s))
          eof (reify)]
      (loop [forms []]
        (let [x (trdr/read rdr nil eof)]
          (if (identical? x eof)
            forms
            (recur (conj forms x))))))))

(defn subforms-with-first-in-set [form sym-set]
  (let [a (atom [])]
    (prewalk (fn [form]
               (when (and (sequential? form)
                          (not (vector? form))
                          (contains? sym-set (first form)))
                 (swap! a conj form))
               form)
             form)
    @a))

(defn replace-subforms-with-first-in-set [form sym-set replace-fn]
  (prewalk (fn [form]
             (if (and (sequential? form)
                      (not (vector? form))
                      (contains? sym-set (first form)))
               (replace-fn form)
               form))
           form))

(defn replace-comments-with-nil [form]
  (replace-subforms-with-first-in-set form #{'comment} (constantly nil)))

(defn- mark-statements-in-try-body-post [ast]
  (if (and (= :try (:op ast))
           (some #{:body} (:children ast))
           (let [body (:body ast)]
             (and (= :do (:op body))
                  (some #{:statements} (:children body))
                  (vector? (:statements body)))))
    (update-in ast [:body :statements]
               (fn [stmts]
                 (mapv #(assoc % :eastwood/unused-ret-val-expr-in-try-body true)
                       stmts)))
    ast))

(defn- mark-exprs-in-try-body-post [ast]
  (if-not (= :try (:op ast))
    ast
    (if-not (some #{:body} (:children ast))
        (do
          (println (format "Found try node but it had no :body key in its :children, only %s"
                           (seq (:children ast))))
          (pprint-ast-node ast)
          ast)
        (let [body (:body ast)]
          (if-not (= :do (:op body))
            (update-in ast [:body]
                       (fn [node]
                         (assoc node
                           :eastwood/used-ret-val-expr-in-try-body true)))
            (let [;; Mark statements - i.e. non-returning expressions in body
                  ast (if-not (and (some #{:statements} (:children body))
                                   (vector? (:statements body)))
                        (do
                          (println (format "Found :try node with :body child that is :do, but either do has no :statements in children (only %s), or it does, but it is not a vector (it has class %s)"
                                           (seq (:children body))
                                           (class (:statements body))))
                          (pprint-ast-node ast)
                          ast)
                        (update-in ast [:body :statements]
                                   (fn [stmts]
                                     (mapv #(assoc % :eastwood/unused-ret-val-expr-in-try-body true)
                                           stmts))))
                  ;; Mark the return expression
                  ast (if-not (some #{:ret} (:children body))
                        (do
                          (println (format "Found :try node with :body child that is :do, but do has no :ret in children (only %s)"
                                           (seq (:children body))))
                          (pprint-ast-node ast)
                          ast)
                        (update-in ast [:body :ret]
                                   (fn [node]
                                     (assoc node
                                       :eastwood/used-ret-val-expr-in-try-body true))))]
              ast))))))

(defn mark-exprs-in-try-body
  "Return an ast that is identical to the argument, except that
expressions 'directly' within try blocks will have one of two new
keywords with value
true.

Statements, i.e. expressions that are not the last one in the body,
and thus their return value is discarded, will have the new
keyword :eastwood/unused-ret-val-expr-in-try-body with value true.
Use the fn statement-in-try-body? to check whether a node was so
marked.

Return values, i.e. expressions that are the last one in the body,
will have the new keyword :eastwood/used-ret-val-expr-in-try-body with
value true.  Use the fn ret-expr-in-try-body? to check whether a node
was so marked.

Use fn expr-in-try-body? to check whether an expression was either one
of these kind."
  [ast]
  (ast/postwalk ast mark-exprs-in-try-body-post))

(defn statement-in-try-body? [ast]
  (contains? ast :eastwood/unused-ret-val-expr-in-try-body))

(defn ret-expr-in-try-body? [ast]
  (contains? ast :eastwood/used-ret-val-expr-in-try-body))

(defn expr-in-try-body? [ast]
  (or (statement-in-try-body? ast)
      (ret-expr-in-try-body? ast)))
