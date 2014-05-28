;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns eastwood.copieddeps.dep1.clojure.tools.analyzer
  "Analyzer for clojure code, host agnostic.

   Entry point:
   * analyze

   Platform implementers must provide dynamic bindings for:
   * macroexpand-1
   * parse
   * create-var
   * var?

   See clojure.tools.analyzer.core-test for an example on how to setup the analyzer."
  (:refer-clojure :exclude [macroexpand-1 macroexpand var? record?])
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.utils :refer :all]))

(defmulti -analyze (fn [op form env & _] op))
(defmulti -parse
  "Takes a form and an env map and dispatches on the head of the form, that is
   a special form."
  (fn [[op & rest] env] op)
  :default :invoke)

(defn analyze-form
  "Like analyze, but does not mark the form with :top-level true"
  [form env]
  (let [form (if (seq? form)
               (or (seq form) ()) ;; force evaluation for analysis
               form)]
    (cond

     (symbol? form)          (-analyze :symbol form env)

     (type? form)            (-analyze :const  form env :type)
     (record? form)          (-analyze :const  form env :record) ;; since recors are maps too, record? *needs*
     ;; to be before map?
     (and (seq? form)
          (not (empty? form))) (-analyze :seq    form env) ;; handles function/macro/special-form invocations

     (vector? form)          (-analyze :vector form env)
     (map? form)             (-analyze :map    form env)
     (set? form)             (-analyze :set    form env)

     :else                   (-analyze :const  form env))))

(defn analyze
  "Given a form to analyze and an environment, a map containing:
   * :locals     a map from binding symbol to AST of the binding value
   * :context    a keyword describing the form's context, one of:
    ** :return     the form is in return position
    ** :statement  the return value of the form is not needed
    ** :expr       everything else
   * :ns         a symbol representing the current namespace of the form to be
                 analyzed, must be present in the :namespaces map
   * :namespaces an atom containing a map from namespace symbol to namespace map,
                 the namespace map contains the following keys:
    ** :mappings   a map of mappings of the namespace, symbol to var/class
    ** :aliases    a map of the aliases of the namespace, symbol to symbol
    ** :ns         a symbol representing the namespace

   returns an AST for that form.

   Every node in the AST is a map that is *guaranteed* to have the following keys:
   * :op   a keyword describing the AST node
   * :form the form represented by the AST node
   * :env  the environment map of the AST node

   Additionaly if the AST node contains sub-nodes, it is guaranteed to have:
   * :children a vector of the keys of the AST node mapping to the sub-nodes,
               ordered, when that makes sense

   It is considered a node either the top-level node (marked with :top-level true)
   or a node that can be reached via :children; if a node contains a node-like
   map that is not reachable by :children, there's no guarantee that such a map
   will contain the guaranteed keys."

  [form env]
  (assoc (analyze-form form env) :top-level true))

(defn empty-env
  "Returns an empty env"
  []
  {:context    :expr
   :locals     {}
   :ns         'user
   :namespaces (atom
                {'user {:mappings {}
                        :aliases  {}
                        :ns       'user}})})

(defn analyze-in-env
  "Takes an env map and returns a function that analyzes a form in that env"
  [env]
  (fn [form] (analyze-form form env)))

(def ^{:dynamic  true
       :arglists '([form env])
       :doc      "If form represents a macro form, returns its expansion,
                  else returns form."}
  macroexpand-1)

(def ^{:dynamic  true
       :arglists '([[op & args] env])
       :doc      "Multimethod that dispatches on op, should default to -parse"}
  parse)

(def ^{:dynamic  true
       :arglists '([sym env])
       :doc      "Creates a var for sym and returns it"}
  create-var)

(def ^{:dynamic  true
       :arglists '([obj])
       :doc      "Returns true if obj represent a var form as returned by create-var"}
  var?)

;; this node wraps non-quoted collections literals with metadata attached
;; to them, the metadata will be evaluated at run-time, not treated like a constant
(defn wrapping-meta
  [{:keys [form env] :as expr}]
  (let [meta (meta form)]
    (if (and (obj? form)
             (seq meta))
      {:op       :with-meta
       :env      env
       :form     form
       :meta     (analyze-form meta (ctx env :expr))
       :expr     (assoc-in expr [:env :context] :expr)
       :children [:meta :expr]}
      expr)))

(defmethod -analyze :const
  [_ form env & [type]]
  (let [type (or type (classify form))
        m (meta form)]
    (merge
     {:op       :const
      :env      env
      :type     type
      :literal? true
      :val      form
      :form     form}
     (when (and (obj? form)
                (seq m))
       {:meta     (-analyze :const m (ctx env :expr) :map) ;; metadata on a constant literal will not be evaluated at
                                                           ;; runtime, this is also true for metadata on quoted collection literals
        :children [:meta]}))))

(defmethod -analyze :vector
  [_ form env]
  (let [items-env (ctx env :expr)
        items (mapv (analyze-in-env items-env) form)]
    (wrapping-meta
     {:op       :vector
      :env      env
      :items    items
      :form     form
      :children [:items]})))

(defmethod -analyze :map
  [_ form env]
  (let [kv-env (ctx env :expr)
        [keys vals] (reduce-kv (fn [[keys vals] k v]
                                 [(conj keys k) (conj vals v)])
                               [[] []] form)
        ks (mapv (analyze-in-env kv-env) keys)
        vs (mapv (analyze-in-env kv-env) vals)]
    (wrapping-meta
     {:op       :map
      :env      env
      :keys     ks
      :vals     vs
      :form     form
      :children [:keys :vals]})))

(defmethod -analyze :set
  [_ form env]
  (let [items-env (ctx env :expr)
        items (mapv (analyze-in-env items-env) form)]
    (wrapping-meta
     {:op       :set
      :env      env
      :items    items
      :form     form
      :children [:items]})))

(def specials
  "Set of special forms common to every clojure variant"
  '#{do if new quote set! try
     catch throw finally & def .
     let* letfn* loop* recur fn*})

(defn macroexpand
  "Repeatedly calls macroexpand-1 on form until it no longer
   represents a macro form, then returns it."
  [form env]
  (loop [form form]
    (let [mform (macroexpand-1 form env)]
      (if (= mform form)
        mform
        (recur mform)))))

(defmethod -analyze :symbol
  [_ sym env]
  (let [mform (macroexpand-1 sym env)] ;; t.a.j/macroexpand-1 macroexpands Class/Field into (. Class Field)
    (if (= mform sym)
      (merge (if-let [{:keys [mutable children] :as local-binding} (-> env :locals sym)] ;; locals shadow globals
               (merge (dissoc local-binding :init) ;; avoids useless passes later
                      {:op          :local
                       :assignable? (boolean mutable)
                       :children    (vec (remove #{:init} children))})
               (if-let [var (let [v (resolve-var sym env)]
                              (and (var? v) v))]
                 {:op          :var
                  :assignable? (dynamic? var) ;; we cannot statically determine if a Var is in a thread-local context
                                              ;; so checking whether it's dynamic or not is the most we can do
                  :var         var}
                 (if-let [maybe-class (namespace sym)] ;; e.g. js/foo.bar or Long/MAX_VALUE
                   (let [maybe-class (symbol maybe-class)]
                     (if-not (resolve-ns maybe-class env) ;; namespaces shadow classes
                       {:op    :maybe-host-form
                        :class maybe-class
                        :field (symbol (name sym))}
                       (throw (ex-info (str "Could not resolve var: " sym)
                                       (merge {:var mform}
                                              (-source-info sym env))))))
                   {:op    :maybe-class ;; e.g. java.lang.Integer or Long
                    :class mform})))
             {:env  env
              :form mform})
      ;; should this preserve the original form?
      (analyze-form (if (obj? mform)
                      (with-meta mform (meta sym))
                      mform)
               env))))

(defmethod -analyze :seq
  [_ form env]
  (let [op (first form)]
    (when (nil? op)
      (throw (ex-info "Can't call nil"
                      (merge {:form form}
                             (-source-info form env)))))
    (let [mform (macroexpand-1 form env)]
      (if (= form mform)  ;; function/special-form invocation
        (parse mform env)
        (analyze-form mform env)))))

(defmethod -parse 'do
  [[_ & exprs :as form] env]
  (let [statements-env (ctx env :statement)
        [statements ret] (loop [statements [] [e & exprs] exprs]
                           (if (seq exprs)
                             (recur (conj statements e) exprs)
                             [statements e]))
        statements (mapv (analyze-in-env statements-env) statements)
        ret (analyze-form ret env)]
    {:op         :do
     :env        env
     :form       form
     :statements statements
     :ret        ret
     :children   [:statements :ret]}))

(defmethod -parse 'if
  [[_ test then else :as form] env]
  (when-not (<= 3 (count form) 4)
    (throw (ex-info (str "Wrong number of args to if, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  (let [test-expr (analyze-form test (ctx env :expr))
        then-expr (analyze-form then env)
        else-expr (analyze-form else env)]
    {:op       :if
     :form     form
     :env      env
     :test     test-expr
     :then     then-expr
     :else     else-expr
     :children [:test :then :else]}))

(defmethod -parse 'new
  [[_ class & args :as form] env]
  (when-not (>= (count form) 2)
    (throw (ex-info (str "Wrong number of args to new, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  (let [args-env (ctx env :expr)
        args (mapv (analyze-in-env args-env) args)]
    {:op          :new
     :env         env
     :form        form
     :class       class
     :args        args
     :children    [:args]}))

(defmethod -parse 'quote
  [[_ expr :as form] env]
  (when-not (= 2 (count form))
    (throw (ex-info (str "Wrong number of args to quote, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  (let [const (-analyze :const expr env)]
    {:op       :quote
     :expr     const
     :form     form
     :env      env
     :literal? true
     :children [:expr]}))

(defmethod -parse 'set!
  [[_ target val :as form] env]
  (when-not (= 3 (count form))
    (throw (ex-info (str "Wrong number of args to set!, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  (let [target (analyze-form target (ctx env :expr))
        val (analyze-form val (ctx env :expr))]
    {:op       :set!
     :env      env
     :form     form
     :target   target
     :val      val
     :children [:target :val]}))

(defn analyze-body [body env]
  ;; :body is used by emit-form to remove the artificial 'do
  (assoc (parse (cons 'do body) env) :body? true))

(defn valid-binding-symbol? [s]
  (and (symbol? s)
       (not (namespace s))
       (not (.contains (name s) "."))))

(defmethod -parse 'try
  [[_ & body :as form] env]
  (let [catch? (every-pred seq? #(= (first %) 'catch))
        finally? (every-pred seq? #(= (first %) 'finally))
        [body tail'] (split-with (complement (some-fn catch? finally?)) body)
        [cblocks tail] (split-with catch? tail')
        [[fblock & fbs :as fblocks] tail] (split-with finally? tail)]
    (when-not (empty? tail)
      (throw (ex-info "Only catch or finally clause can follow catch in try expression"
                      (merge {:expr tail
                              :form form}
                             (-source-info form env)))))
    (when-not (empty? fbs)
      (throw (ex-info "Only one finally clause allowed in try expression"
                      (merge {:expr fblocks
                              :form form}
                             (-source-info form env)))))
    (if (and (empty? cblocks)
             (empty? fblocks))
      (assoc (analyze-body body env) :form form) ;; discard the useless try
      (let [body (analyze-body body (assoc env :no-recur true :in-try true)) ;; cannot recur across try
            cenv (ctx env :expr)
            cblocks (mapv #(parse % cenv) cblocks)
            fblock (when-not (empty? fblock)
                     (analyze-body (rest fblock) (ctx env :statement)))]
        (merge {:op      :try
                :env     env
                :form    form
                :body    body
                :catches cblocks}
               (when fblock
                 {:finally fblock})
               {:children `[:body :catches ~@(when fblock [:finally])]})))))

(defmethod -parse 'catch
  [[_ etype ename & body :as form] env]
  (when-not (valid-binding-symbol? ename)
    (throw (ex-info (str "Bad binding form: " ename)
                    (merge {:sym ename
                            :form form}
                           (-source-info form env)))))
  (let [local {:op    :binding
               :env   env
               :form  ename
               :name  ename
               :local :catch
               :tag   etype}]
    {:op          :catch
     :class       etype
     :local       local
     :env         env
     :form        form
     :body        (analyze-body body (assoc-in env [:locals ename] (dissoc-env local)))
     :children    [:local :body]}))

(defmethod -parse 'throw
  [[_ throw :as form] env]
  (when-not (= 2 (count form))
    (throw (ex-info (str "Wrong number of args to throw, had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  {:op        :throw
   :env       env
   :form      form
   :exception (analyze-form throw (ctx env :expr))
   :children  [:exception]})

(defn validate-bindings
  [[op bindings & _ :as form] env]
  (when-let [error-msg
             (cond
              (not (vector? bindings))
              (str op " requires a vector for its bindings, had: "
                   (class bindings))

              (not (even? (count bindings)))
              (str op " requires an even number of forms in binding vector, had: "
                   (count bindings)))]
    (throw (ex-info error-msg
                    (merge {:form     form
                            :bindings bindings}
                           (-source-info form env))))))

(defmethod -parse 'letfn*
  [[_ bindings & body :as form] env]
  (validate-bindings form env)
  (let [bindings (apply hash-map bindings) ;; non-determinalistically pick only one local with the same name,
                                           ;; if more are present.
        fns (keys bindings)]
    (when-let [[sym] (seq (remove valid-binding-symbol? fns))]
      (throw (ex-info (str "Bad binding form: " sym)
                      (merge {:form form
                              :sym  sym}
                             (-source-info form env)))))
    (let [binds (reduce (fn [binds name]
                          (assoc binds name
                                 {:op    :binding
                                  :env   env
                                  :name  name
                                  :form  name
                                  :local :letfn}))
                        {} fns)
          e (update-in env [:locals] merge binds) ;; pre-seed locals
          binds (reduce-kv (fn [binds name bind]
                             (assoc binds name
                                    (merge bind
                                           {:init     (analyze-form (bindings name)
                                                               (ctx e :expr))
                                            :children [:init]})))
                           {} binds)
          e (update-in env [:locals] merge (update-vals binds dissoc-env))
          body (analyze-body body e)]
      {:op       :letfn
       :env      env
       :form     form
       :bindings (vec (vals binds)) ;; order is irrelevant
       :body     body
       :children [:bindings :body]})))

(defn analyze-let
  [[op bindings & body :as form] {:keys [context loop-id] :as env}]
  (validate-bindings form env)
  (let [loop? (= 'loop* op)]
    (loop [bindings bindings
           env (ctx env :expr)
           binds []]
      (if-let [[name init & bindings] (seq bindings)]
        (if (not (valid-binding-symbol? name))
          (throw (ex-info (str "Bad binding form: " name)
                          (merge {:form form
                                  :sym  name}
                                 (-source-info form env))))
          (let [init-expr (analyze-form init env)
                bind-expr {:op       :binding
                           :env      env
                           :name     name
                           :init     init-expr
                           :form     name
                           :local    (if loop? :loop :let)
                           :children [:init]}]
            (recur bindings
                   (assoc-in env [:locals name] (dissoc-env bind-expr))
                   (conj binds bind-expr))))
        (let [body-env (assoc env :context (if loop? :return context))
              body (analyze-body body (merge body-env
                                             (when loop?
                                               {:loop-id     loop-id
                                                :loop-locals (count binds)})))]
          {:body     body
           :bindings binds
           :children [:bindings :body]})))))

(defmethod -parse 'let*
  [form env]
  (into {:op   :let
         :form form
         :env  env}
        (analyze-let form env)))

(defmethod -parse 'loop*
  [form env]
  (let [loop-id (gensym "loop_") ;; can be used to find matching recur
        env (dissoc (assoc env :loop-id loop-id) :no-recur)]
    (into {:op      :loop
           :form    form
           :env     env
           :loop-id loop-id}
          (analyze-let form env))))

(defmethod -parse 'recur
  [[_ & exprs :as form] {:keys [context loop-locals loop-id no-recur]
                         :as env}]
  (when-let [error-msg
             (cond
              (not (= :return context))
              "Can only recur from tail position"

              no-recur
              "Cannot recur across try"

              (not (= (count exprs) loop-locals))
              (str "Mismatched argument count to recur, expected: " loop-locals
                   " args, had: " (count exprs)))]
    (throw (ex-info error-msg
                    (merge {:exprs exprs
                            :form  form}
                           (-source-info form env)))))

  (let [exprs (mapv (analyze-in-env (ctx env :expr)) exprs)]
    {:op          :recur
     :env         env
     :form        form
     :exprs       exprs
     :loop-id     loop-id
     :children    [:exprs]}))

(defn analyze-fn-method [[params & body :as form] {:keys [locals local] :as env}]
  (when (not-every? valid-binding-symbol? params)
    (throw (ex-info (str "Params must be valid binding symbols, had: "
                         (mapv class params))
                    (merge {:params params
                            :form   form}
                           (-source-info form env)
                           (-source-info params env))))) ;; more specific
  (when-not (vector? params)
    (throw (ex-info "Parameter declaration should be a vector"
                    (merge {:params params
                            :form   form}
                           (-source-info form env)
                           (-source-info params env)))))
  (let [variadic? (boolean (some '#{&} params))
        params-names (if variadic? (vec (remove '#{&} params)) params)
        env (dissoc env :local)
        arity (count params-names)
        params-expr (mapv (fn [name id]
                            {:env       env
                             :form      name
                             :name      name
                             :variadic? (and variadic?
                                             (= id (dec arity)))
                             :op        :binding
                             :arg-id    id
                             :local     :arg})
                          params-names (range))
        fixed-arity (if variadic?
                      (dec arity)
                      arity)
        loop-id (gensym "loop_")
        body-env (into (update-in env [:locals]
                                  merge (zipmap params-names (map dissoc-env params-expr)))
                       {:context     :return
                        :loop-id     loop-id
                        :loop-locals (count params-expr)})
        body (analyze-body body body-env)]
    (when variadic?
      (let [x (drop-while #(not= % '&) params)]
        (when (contains? #{nil '&} (second x))
          (throw (ex-info "Invalid parameter list"
                          (merge {:params params
                                  :form   form}
                                 (-source-info form env)
                                 (-source-info params env)))))
        (when (not= 2 (count x))
          (throw (ex-info (str "Unexpected parameter: " (first (drop 2 x))
                               " after variadic parameter: " (second x))
                          (merge {:params params
                                  :form   form}
                                 (-source-info form env)
                                 (-source-info params env)))))))
    (merge
     {:op          :fn-method
      :form        form
      :loop-id     loop-id
      :env         env
      :variadic?   variadic?
      :params      params-expr
      :fixed-arity fixed-arity
      :body        body
      :children    [:params :body]}
     (when local
       {:local (dissoc local :env)}))))

(defmethod -parse 'fn*
  [[op & args :as form] env]
  (let [[n meths] (if (symbol? (first args))
                    [(first args) (next args)]
                    [nil (seq args)])
        name-expr {:op    :binding
                   :env   env
                   :form  n
                   :local :fn
                   :name  n}
        e (if n (assoc (assoc-in env [:locals n] (dissoc-env name-expr)) :local name-expr) env)
        once? (-> op meta :once boolean)
        menv (assoc (dissoc e :no-recur :in-try) :once once?)
        meths (if (vector? (first meths)) (list meths) meths) ;;turn (fn [] ...) into (fn ([]...))
        methods-exprs (mapv #(analyze-fn-method % menv) meths)
        variadic (seq (filter :variadic? methods-exprs))
        variadic? (boolean variadic)
        fixed-arities (seq (map :fixed-arity (remove :variadic? methods-exprs)))
        max-fixed-arity (when fixed-arities (apply max fixed-arities))]
    (when (>= (count variadic) 2)
      (throw (ex-info "Can't have more than 1 variadic overload"
                      (merge {:variadics (mapv :form variadic)
                              :form      form}
                             (-source-info form env)))))
    (when (not= (seq (distinct fixed-arities)) fixed-arities)
      (throw (ex-info "Can't have 2 or more overloads with the same arity"
                      (merge {:form form}
                             (-source-info form env)))))
    (when (and variadic?
               (not-every? #(<= (:fixed-arity %)
                          (:fixed-arity (first variadic)))
                      (remove :variadic? methods-exprs)))
      (throw (ex-info "Can't have fixed arity overload with more params than variadic overload"
                      (merge {:form form}
                             (-source-info form env)))))
    (merge {:op              :fn
            :env             env
            :form            form
            :variadic?       variadic?
            :max-fixed-arity max-fixed-arity
            :methods         methods-exprs
            :once            once?}
           (when n
             {:local name-expr})
           {:children `[~@(when n [:local]) :methods]})))

(defmethod -parse 'def
  [[_ sym & expr :as form] {:keys [ns namespaces] :as env}]
  (when (not (symbol? sym))
    (throw (ex-info (str "First argument to def must be a symbol, had: " (class sym))
                    (merge {:form form}
                           (-source-info form env)))))
  (when (and (namespace sym)
             (not= *ns* (the-ns (symbol (namespace sym)))))
    (throw (ex-info "Cannot def namespace qualified symbol"
                    (merge {:form form
                            :sym sym}
                           (-source-info form env)))))
  (let [pfn (fn
              ([])
              ([init]
                 {:init init})
              ([doc init]
                 {:pre [(string? doc)]}
                 {:init init :doc doc}))
        args (apply pfn expr)

        doc (or (:doc args) (-> sym meta :doc))
        arglists (when-let [arglists (:arglists (meta sym))]
                   (second arglists)) ;; drop quote

        sym (with-meta (symbol (name sym))
              (merge (meta sym)
                     (when arglists
                       {:arglists arglists})
                     (when doc
                       {:doc doc})
                     (-source-info form env)))

        var (create-var sym env) ;; interned var will have quoted arglists, replaced on evaluation
        _ (swap! namespaces assoc-in [ns :mappings sym] var)

        meta (merge (meta sym)
                    (when arglists
                      {:arglists (list 'quote arglists)}))

        meta-expr (when meta (analyze-form meta (ctx env :expr))) ;; meta on def sym will be evaluated

        args (when-let [[_ init] (find args :init)]
               (merge args {:init (analyze-form init (ctx env :expr))}))
        children `[~@(when meta [:meta])
                   ~@(when (:init args) [:init])]]

    (merge {:op   :def
            :env  env
            :form form
            :name sym
            :var  var}
           (when meta
             {:meta meta-expr})
           args
           (when-not (empty? children)
             {:children children}))))

(defmethod -parse '.
  [[_ target & [m-or-f & args] :as form] env]
  (when-not (>= (count form) 3)
    (throw (ex-info (str "Wrong number of args to ., had: " (dec (count form)))
                    (merge {:form form}
                           (-source-info form env)))))
  (let [[m-or-f field?] (if (and (symbol? m-or-f)
                                 (= \- (first (name m-or-f))))
                          [(-> m-or-f name (subs 1) symbol) true]
                          [(if args (cons m-or-f args) m-or-f) false])
        target-expr (analyze-form target (ctx env :expr))
        call? (and (not field?) (seq? m-or-f))]

    (when (and call? (not (symbol? (first m-or-f))))
      (throw (ex-info (str "Method name must be a symbol, had: " (class (first m-or-f)))
                      (merge {:form   form
                              :method m-or-f}
                             (-source-info form env)))))
    (merge {:form   form
            :env    env
            :target target-expr}
           (cond
            call?
            {:op       :host-call
             :method   (symbol (name (first m-or-f)))
             :args     (mapv (analyze-in-env (ctx env :expr)) (next m-or-f))
             :children [:target :args]}

            field?
            {:op       :host-field
             :field    (symbol (name m-or-f))
             :children [:target]}

            :else
            {:op       :host-interop ;; either field access or no-args method call
             :m-or-f   (symbol (name m-or-f))
             :children [:target]}))))

(defmethod -parse :invoke
  [[f & args :as form] env]
  (let [e (ctx env :expr)
        fn-expr (analyze-form f e)
        args-expr (mapv (analyze-in-env e) args)
        m (meta form)]
    (merge {:op   :invoke
            :form form
            :env  env
            :fn   fn-expr
            :args args-expr}
           (when m
             {:meta m}) ;; meta on invoke form will not be evaluated
           {:children [:fn :args]})))
