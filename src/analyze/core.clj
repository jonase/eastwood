(set! *warn-on-reflection* false)

(ns analyze.core
  "Interface to Compiler's analyze.
  Entry point `analyze-path` and `analyze-one`"
  (:import (java.io LineNumberReader InputStreamReader PushbackReader)
           (clojure.lang RT Compiler$DefExpr Compiler$LocalBinding Compiler$BindingInit Compiler$LetExpr
                         Compiler$LetFnExpr Compiler$StaticMethodExpr Compiler$InstanceMethodExpr Compiler$StaticFieldExpr
                         Compiler$NewExpr Compiler$LiteralExpr Compiler$EmptyExpr Compiler$VectorExpr Compiler$MonitorEnterExpr
                         Compiler$MonitorExitExpr Compiler$ThrowExpr Compiler$InvokeExpr Compiler$TheVarExpr Compiler$VarExpr
                         Compiler$UnresolvedVarExpr Compiler$ObjExpr Compiler$NewInstanceMethod Compiler$FnMethod Compiler$FnExpr
                         Compiler$NewInstanceExpr Compiler$MetaExpr Compiler$BodyExpr Compiler$ImportExpr Compiler$AssignExpr
                         Compiler$TryExpr$CatchClause Compiler$TryExpr Compiler$C Compiler$LocalBindingExpr Compiler$RecurExpr
                         Compiler$MapExpr Compiler$IfExpr Compiler$KeywordInvokeExpr Compiler$InstanceFieldExpr Compiler$InstanceOfExpr
                         Compiler$CaseExpr Compiler$Expr Compiler$SetExpr Compiler$MethodParamExpr))
  (:require [clojure.reflect :as reflect]
            [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.string :as string]))

(defn field-accessor [class-obj field obj]
  (let [field (.getDeclaredField class-obj (name field))]
    (.setAccessible field true)
    (let [ret (.get field obj)]
      (if (instance? Boolean ret)
        (boolean ret)
        ret))))

(defn method-accessor [class-obj method obj types & args]
  (let [method (.getDeclaredMethod class-obj (name method) (into-array Class types))]
    (.setAccessible method true)
    (.invoke method obj (object-array args))))

(defmulti Expr->map (fn Expr->map [& args]
                      (assert (re-find #"Expr" (.getSimpleName (class (first args)))))
                      (assert (= 2 (count args)))
                      (-> args first class)))

;; def

(defmethod Expr->map Compiler$DefExpr
  [^Compiler$DefExpr expr env]
  (let [field (partial field-accessor Compiler$DefExpr)
        init (Expr->map (field 'init expr) env)
        meta (when-let [meta (field 'meta expr)]
               (Expr->map meta env))]
    {:op :def
     :env (assoc env 
            :source (field 'source expr)
            :line (field 'line expr))
     :var (field 'var expr)
     :meta meta
     :init init
     :children [meta init]
     :init-provided (field 'initProvided expr)
     :is-dynamic (field 'isDynamic expr)
     :Expr-obj expr}))

;; let

(defn LocalBinding->map [^Compiler$LocalBinding lb env]
  (let [init (when-let [init (.init lb)]
               (Expr->map init env))]
    {:op :local-binding
     :env env
     :sym (.sym lb)
     :tag (.tag lb)
     :init init
     :children (when init [init])
     :LocalBinding-obj lb}))

(defn BindingInit->vec [^Compiler$BindingInit bi env]
  (let [local-binding (LocalBinding->map (.binding bi) env)
        init (Expr->map (.init bi) env)]
    {:op :binding-init
     :local-binding local-binding
     :init init
     :children [local-binding init]
     :BindingInit-obj bi}))

(defmethod Expr->map Compiler$LetExpr
  [^Compiler$LetExpr expr env]
  (let [body (Expr->map (.body expr) env)
        bindings (-> (doall (map BindingInit->vec (.bindingInits expr) (repeat env)))
                   vec)]
    {:op :let
     :env env
     :bindings bindings
     :body body
     :children (conj bindings body)
     :Expr-obj expr}))

;; letfn

(defmethod Expr->map Compiler$LetFnExpr
  [^Compiler$LetFnExpr expr env]
  (let [body (Expr->map (.body expr) env)
        binding-inits (-> (doall (map BindingInit->vec (.bindingInits expr)))
                        vec)]
    {:op :letfn
     :env env
     :body body
     :binding-inits binding-inits
     :children (conj binding-inits body)
     :Expr-obj expr}))

;; LocalBindingExpr

(defmethod Expr->map Compiler$LocalBindingExpr
  [^Compiler$LocalBindingExpr expr env]
  (let [local-binding (LocalBinding->map (.b expr) env)]
    {:op :local-binding-expr
     :env env
     :local-binding local-binding
     :tag (.tag expr)
     :children [local-binding]
     :Expr-obj expr}))

;; Methods

(defmethod Expr->map Compiler$StaticMethodExpr
  [^Compiler$StaticMethodExpr expr env]
  (let [field (partial field-accessor Compiler$StaticMethodExpr)
        args (doall (map Expr->map (field 'args expr) (repeat env)))]
    {:op :static-method 
     :env (assoc env
            :source (field 'source expr)
            :line (field 'line expr))
     :class (field 'c expr)
     :method-name (field 'methodName expr)
     :method (when-let [method (field 'method expr)]
               (@#'reflect/method->map method))
     :args args
     :tag (field 'tag expr)
     :children args
     :Expr-obj expr}))

(defmethod Expr->map Compiler$InstanceMethodExpr
  [^Compiler$InstanceMethodExpr expr env]
  (let [field (partial field-accessor Compiler$InstanceMethodExpr)
        args (doall (map Expr->map (field 'args expr) (repeat env)))]
    {:op :instance-method 
     :env (assoc env
            :source (field 'source expr)
            :line (field 'line expr))
     :target (field 'target expr)
     :method-name (field 'methodName expr)
     :method (when-let [method (field 'method expr)]
               (@#'reflect/method->map method))
     :args args
     :tag (field 'tag expr)
     :children args
     :Expr-obj expr}))

;; Fields

(defmethod Expr->map Compiler$StaticFieldExpr
  [^Compiler$StaticFieldExpr expr env]
  (let [field (partial field-accessor Compiler$StaticFieldExpr)]
    {:op :static-field
     :env (assoc env
            :line (field 'line expr))
     :class (field 'c expr)
     :field-name (field 'fieldName expr)
     :field (when-let [field (field 'field expr)]
              (@#'reflect/field->map field))
     :tag (field 'tag expr)
     :Expr-obj expr}))

(defmethod Expr->map Compiler$InstanceFieldExpr
  [^Compiler$InstanceFieldExpr expr env]
  (let [field (partial field-accessor Compiler$InstanceFieldExpr)
        target (Expr->map (field 'target expr) env)]
    {:op :static-field
     :env (assoc env
            :line (field 'line expr))
     :target target
     :target-class (field 'targetClass expr)
     :field (when-let [field (field 'field expr)]
              (@#'reflect/field->map field))
     :field-name (field 'fieldName expr)
     :tag (field 'tag expr)
     :children [target]}))

(defmethod Expr->map Compiler$NewExpr
  [^Compiler$NewExpr expr env]
  (let [args (doall (map Expr->map (.args expr) (repeat env)))]
    {:op :new 
     :env env
     :ctor (when-let [ctor (.ctor expr)]
             (@#'reflect/constructor->map ctor))
     :class (.c expr)
     :args args
     :children args
     :Expr-obj expr}))

;; Literals

(defmethod Expr->map Compiler$LiteralExpr
  [^Compiler$LiteralExpr expr env]
  (let [method (partial method-accessor Compiler$LiteralExpr)]
    {:op :literal
     :env env
     :val (method 'val expr [])
     :Expr-obj expr}))

(defmethod Expr->map Compiler$EmptyExpr
  [^Compiler$EmptyExpr expr env]
  {:op :empty-expr
   :env env
   :coll (.coll expr)
   :Expr-obj expr})

;; set literal

(defmethod Expr->map Compiler$SetExpr
  [^Compiler$SetExpr expr env]
  (let [keys (doall (map Expr->map (.keys expr) (repeat env)))]
    {:op :set
     :env env
     :keys keys
     :children keys
     :Expr-obj expr}))

;; vector literal

(defmethod Expr->map Compiler$VectorExpr
  [^Compiler$VectorExpr expr env]
  (let [args (doall (map Expr->map (.args expr) (repeat env)))]
    {:op :vector
     :env env
     :args args
     :children args
     :Expr-obj expr}))

;; map literal

(defmethod Expr->map Compiler$MapExpr
  [^Compiler$MapExpr expr env]
  (let [keyvals (doall (map Expr->map (.keyvals expr) (repeat env)))]
    {:op :map
     :env env
     :keyvals keyvals
     :children keyvals
     :Expr-obj expr}))

;; Untyped

(defmethod Expr->map Compiler$MonitorEnterExpr
  [^Compiler$MonitorEnterExpr expr env]
  (let [field (partial field-accessor Compiler$MonitorEnterExpr)
        target (Expr->map (field 'target expr) env)]
    {:op :monitor-enter
     :env env
     :target target
     :children [target]
     :Expr-obj expr}))

(defmethod Expr->map Compiler$MonitorExitExpr
  [^Compiler$MonitorExitExpr expr env]
    (let [field (partial field-accessor Compiler$MonitorExitExpr)
          target (Expr->map (field 'target expr) env)]
      {:op :monitor-exit
       :env env
       :target target
       :children [target]
       :Expr-obj expr}))

(defmethod Expr->map Compiler$ThrowExpr
  [^Compiler$ThrowExpr expr env]
  (let [field (partial field-accessor Compiler$ThrowExpr)
        exception (Expr->map (field 'excExpr expr) env)]
    {:op :throw
     :env env
     :exception exception
     :children [exception]
     :Expr-obj expr}))

;; Invokes

(defmethod Expr->map Compiler$InvokeExpr
  [^Compiler$InvokeExpr expr env]
  (let [field (partial field-accessor Compiler$InvokeExpr)
        fexpr (Expr->map (field 'fexpr expr) env)
        args (doall (map Expr->map (field 'args expr) (repeat env)))]
    (merge
     {:op :invoke
      :env (assoc env
             :line (field 'line expr)
             :source (field 'source expr))
      :fexpr fexpr
      :tag (field 'tag expr)
      :args args
      :is-protocol (field 'isProtocol expr)
      :is-direct (field 'isDirect expr)
      :site-index (field 'siteIndex expr)
      :protocol-on (field 'protocolOn expr)
      :children (cons fexpr args)
      :Expr-obj expr}
     (when-let [m (field 'onMethod expr)]
       {:method (@#'reflect/method->map m)}))))

(defmethod Expr->map Compiler$KeywordInvokeExpr
  [^Compiler$KeywordInvokeExpr expr env]
  (let [field (partial field-accessor Compiler$KeywordInvokeExpr)
        target (Expr->map (field 'target expr) env)]
    {:op :keyword-invoke
     :env (assoc env
            :line (field 'line expr)
            :source (field 'source expr))
     :kw (field 'kw expr)
     :tag (field 'tag expr)
     :target target
     :children [target]
     :Expr-obj expr}))

;; TheVarExpr

(defmethod Expr->map Compiler$TheVarExpr
  [^Compiler$TheVarExpr expr env]
  {:op :the-var
   :env env
   :var (.var expr)
   :Expr-obj expr})

;; VarExpr

(defmethod Expr->map Compiler$VarExpr
  [^Compiler$VarExpr expr env]
  {:op :var
   :env env
   :var (.var expr)
   :tag (.tag expr)
   :Expr-obj expr})

;; UnresolvedVarExpr

(defmethod Expr->map Compiler$UnresolvedVarExpr
  [^Compiler$UnresolvedVarExpr expr env]
  (let [field (partial field-accessor Compiler$UnresolvedVarExpr)]
    {:op :unresolved-var
     :env env
     :sym (field 'symbol expr)
     :Expr-obj expr}))

;; ObjExprs

(defmethod Expr->map Compiler$ObjExpr
  [^Compiler$ObjExpr expr env]
  {:op :obj-expr
   :env env
   :tag (.tag expr)
   :Expr-obj expr})

;; FnExpr (extends ObjExpr)

(defmulti ObjMethod->map (fn [& args] 
                           (assert (= 2 (count args)))
                           (-> args first class)))

(defmethod ObjMethod->map Compiler$NewInstanceMethod 
  [^Compiler$NewInstanceMethod obm env]
  (let [body (Expr->map (.body obm) env)]
    {:op :new-instance-method
     :env env
     :body body
     :children [body]
     :ObjMethod-obj obm}))

(defmethod ObjMethod->map Compiler$FnMethod 
  [^Compiler$FnMethod obm env]
  (let [body (Expr->map (.body obm) env)
        required-params (doall (map LocalBinding->map (.reqParms obm) (repeat env)))]
    {:op :fn-method
     :env env
     :body body
     ;; Map LocalExpr@xx -> LocalExpr@xx
     ;   :locals (map Expr->map (keys (.locals obm)) (repeat env))
     :required-params required-params
     :rest-param (let [rest-param (.restParm obm)]
                   (if rest-param
                     (LocalBinding->map rest-param env)
                     rest-param))
     :children [body]
     :ObjMethod-obj obm}))

(defmethod Expr->map Compiler$FnExpr
  [^Compiler$FnExpr expr env]
  (let [methods (doall (map ObjMethod->map (.methods expr) (repeat env)))]
    {:op :fn-expr
     :env env
     :methods methods
     :variadic-method (when-let [variadic-method (.variadicMethod expr)]
                        (ObjMethod->map variadic-method env))
     :tag (.tag expr)
     :children methods
     :Expr-obj expr}))

;; NewInstanceExpr

(defmethod Expr->map Compiler$NewInstanceExpr
  [^Compiler$NewInstanceExpr expr env]
  (let [field (partial field-accessor Compiler$NewInstanceExpr)
        methods (doall (map ObjMethod->map (field 'methods expr) (repeat env)))]
    {:op :new-instance-expr
     :env env
     :methods methods
     :mmap (field 'mmap expr)
     :covariants (field 'covariants expr)
     :tag (.tag expr)
     :children methods
     :Expr-obj expr}))

;; InstanceOfExpr

(defmethod Expr->map Compiler$InstanceOfExpr
  [^Compiler$InstanceOfExpr expr env]
  (let [field (partial field-accessor Compiler$InstanceOfExpr)
        exp (Expr->map (field 'expr expr) env)]
    {:op :instance-of
     :class (field 'c expr)
     :the-expr exp
     :children [exp]
     :Expr-obj expr}))

;; MetaExpr

(defmethod Expr->map Compiler$MetaExpr
  [^Compiler$MetaExpr expr env]
  (let [meta (Expr->map (.meta expr) env)
        the-expr (Expr->map (.expr expr) env)]
    {:op :meta
     :env env
     :meta meta
     :expr the-expr
     :children [meta the-expr]
     :Expr-obj expr}))

;; do

(defmethod Expr->map Compiler$BodyExpr
  [^Compiler$BodyExpr expr env]
  (let [exprs (doall (map Expr->map (.exprs expr) (repeat env)))]
    {:op :do
     :env env
     :exprs exprs
     :children exprs
     :Expr-obj expr}))

;; if

(defmethod Expr->map Compiler$IfExpr
  [^Compiler$IfExpr expr env]
  (let [test (Expr->map (.testExpr expr) env)
        then (Expr->map (.thenExpr expr) env)
        else (Expr->map (.elseExpr expr) env)]
    {:op :if
     :env (assoc env
                 :line (.line expr))
     :test test
     :then then
     :else else
     :children [test then else]
     :Expr-obj expr}))

;; case

(defmethod Expr->map Compiler$CaseExpr
  [^Compiler$CaseExpr expr env]
  (let [the-expr (Expr->map (.expr expr) env)
        tests (doall (map Expr->map (vals (.tests expr)) (repeat env)))
        thens (doall (map Expr->map (vals (.thens expr)) (repeat env)))
        default (Expr->map (.defaultExpr expr) env)]
    {:op :case*
     :the-expr the-expr
     :tests tests
     :thens thens
     :default default
     :children (concat [the-expr] tests thens [default])
     :Expr-obj expr}))


;; ImportExpr

(defmethod Expr->map Compiler$ImportExpr
  [^Compiler$ImportExpr expr env]
  {:op :import*
   :env env
   :class-str (.c expr)
   :Expr-obj expr})

;; AssignExpr (set!)

(defmethod Expr->map Compiler$AssignExpr
  [^Compiler$AssignExpr expr env]
  (let [target (Expr->map (.target expr) env)
        val (Expr->map (.val expr) env)]
    {:op :set!
     :env env
     :target target
     :val val
     :children [target val]
     :Expr-obj expr}))

;; TryExpr

(defn CatchClause->map [^Compiler$TryExpr$CatchClause ctch env]
  (let [local-binding (LocalBinding->map (.lb ctch) env)
        handler (Expr->map (.handler ctch) env)]
    {:op :catch
     :env env
     :class (.c ctch)
     :local-binding local-binding
     :handler handler
     :children [local-binding handler]
     :CatchClause-obj ctch}))

(defmethod Expr->map Compiler$TryExpr
  [^Compiler$TryExpr expr env]
  (let [try-expr (Expr->map (.tryExpr expr) env)
        finally-expr (when-let [finally-expr (.finallyExpr expr)]
                       (Expr->map finally-expr env))
        catch-exprs (doall (map CatchClause->map (.catchExprs expr) (repeat env)))]
    {:op :try
     :env env
     :try-expr try-expr
     :finally-expr finally-expr
     :catch-exprs catch-exprs
     :ret-local (.retLocal expr)
     :finally-local (.finallyLocal expr)
     :children (concat [try-expr] (when finally-expr [finally-expr]) catch-exprs)
     :Expr-obj expr}))

;; RecurExpr

(defmethod Expr->map Compiler$RecurExpr
  [^Compiler$RecurExpr expr env]
  (let [field (partial field-accessor Compiler$RecurExpr)
        loop-locals (doall (map LocalBinding->map (.loopLocals expr) (repeat env)))
        args (doall (map Expr->map (.args expr) (repeat env)))]
    {:op :recur
     :env (assoc env
            :line (field 'line expr)
            :source (field 'source expr))
     :loop-locals loop-locals
     :args args
     :children (concat loop-locals args)
     :Expr-obj expr}))

(defmethod Expr->map Compiler$MethodParamExpr
  [expr & args]
  ;; TODO
  {})

(defmethod Expr->map :default
  [expr & args]
  (println expr)
  (throw (Exception. (str "No method in multimethod 'Expr->map' for dispatch value: " (class expr)))))


(defn wall-hack-method [class-name method-name types obj & args]
  (-> class-name (.getDeclaredMethod (name method-name)
                                     (into-array Class (seq types)))
    (doto (.setAccessible true))
    (.invoke obj (into-array Object args))))

(defn- analyze* [env form]
  (letfn [(invoke-analyze [context form]
            (push-thread-bindings {Compiler/LOADER (RT/makeClassLoader)})
            (try
              (Compiler/analyze context form)
              (finally
                (pop-thread-bindings))))]
    (let [context (case (:context env)
                    :statement Compiler$C/STATEMENT
                    :expression Compiler$C/EXPRESSION
                    :return Compiler$C/RETURN
                    :eval Compiler$C/EVAL)
          exprs (try
                  (invoke-analyze context form)
                  (catch RuntimeException e
                    (throw (repl/root-cause e))))]
      (Expr->map exprs (merge-with conj (dissoc env :context) {:locals {}})))))

(defn analyze-one [env form]
  (binding [*ns* (find-ns (-> env :ns :name))]
    (analyze* env form)))

(defn forms-seq
  "Seq of forms in a Clojure or ClojureScript file."
  ([f]
     (forms-seq f (java.io.PushbackReader. (io/reader f))))
  ([f ^java.io.PushbackReader rdr]
     (if-let [form (read rdr nil nil)]
       (lazy-seq (cons form (forms-seq f rdr)))
       (.close rdr))))

(defn analyze-path 
  "Takes a path and a namespace symbol.
  Returns a seq of maps, with keys :op, :env. If expressions
  have children, will have :children entry."
  [source-path ns]
  (require ns)
  (let [strm (.getResourceAsStream (RT/baseLoader) source-path)]
    (with-open [rdr (PushbackReader. (InputStreamReader. strm))]
      (let [frms (forms-seq nil rdr)
            afn #(let [env {:ns {:name ns} :context :eval :locals {}}]
                   (analyze* env %))]
        (binding [*ns* (find-ns ns)]
          (doall (map afn frms)))))))

(comment
(analyze-one {:ns {:name 'clojure.core} :context :eval} '(try (throw (Exception.)) (catch Exception e (throw e)) (finally 33)))
(analyze-one {:ns {:name 'clojure.core} :context :eval} '(try ))

;; Expecting more output from things like :fn-method
(analyze-one {:ns {:name 'clojure.core} :context :eval} '(try (println 1 23) (throw (Exception.)) (catch Exception e (throw e)) ))

(analyze-one {:ns {:name 'clojure.core} :context :eval} '(let [b 1] (fn [& a] 1)))

(analyze-one {:ns {:name 'clojure.core} :context :eval} '(Integer. (+ 1 1)))
(analyze-one {:ns {:name 'clojure.core} :context :eval} '(Integer. (+ 1 1)))

(analyze-one {:ns {:name 'clojure.core} :context :eval} '(map io/file [1 2]))

  )

(comment

(def docm
  (analyze-one {:ns {:name 'clojure.repl} :context :eval}
    '(defn doc
      [name]
      "Prints documentation for a var or special form given its name"
      (+ 1 1))))


  (defn traverse [exp]
    (println "op" (:op exp))
    (when-let [children (seq (:children exp))]
      (doseq [c children]
        (traverse c))))

  (traverse docm)
  )
