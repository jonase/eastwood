(ns eastwood.index.tx
  (:require [datomic.api :as d]))

(defmulti tx-data :op)

(defn indexed-tx-data [nodes] 
  (vec (map-indexed (fn [i node]
                      (assoc (tx-data node)
                        :ast/idx i))
                    nodes)))

(defmethod tx-data :host-interop
  [{:keys [target]}]
  {:ast/op :ast.op/host-interop
   :ast/target (tx-data target)})

(defmethod tx-data :set!
  [{:keys [target val]}]
  {:ast/op :ast.op/set!
   :ast/target (tx-data target)
   :ast/val (tx-data val)})

(defmethod tx-data :import
  [{:keys []}]
  {:ast/op :ast.op/import})

(defmethod tx-data :instance-call
  [{:keys [instance args]}]
  (cond-> {:ast/op :ast.op/instance-call
           :ast/instance (tx-data instance)}
          args (assoc :ast/args
                 (indexed-tx-data args))))

(defmethod tx-data :with-meta
  [{:keys [meta expr]}]
  {:ast/op :ast.op/with-meta
   :ast/meta (tx-data meta)
   :ast/expr (tx-data expr)})

(defmethod tx-data :recur
  [{:keys [exprs]}]
  {:ast/op :ast.op/recur
   :ast/exprs (indexed-tx-data exprs)})

(defmethod tx-data :vector
  [{:keys [items]}]
  {:ast/op :ast.op/vector
   :ast/items (indexed-tx-data items)})

(defmethod tx-data :instance-field
  [{:keys [instance]}]
  {:ast/op :ast.op/instance-field
   :ast/instance (tx-data instance)})

(defmethod tx-data :map
  [{:keys [keys vals]}]
  {:ast/op :ast.op/map
   :ast/keys (indexed-tx-data keys)
   :ast/vals (indexed-tx-data vals)})

(defmethod tx-data :keyword-invoke
  [{:keys [fn args]}]
  {:ast/op :ast.op/keyword-invoke
   :ast/fn (tx-data fn)
   :ast/args (indexed-tx-data args)})

(defmethod tx-data :static-call
  [{:keys [args]}]
  (cond-> {:ast/op :ast.op/static-call}
          args (assoc :ast/args (indexed-tx-data args))))

(defmethod tx-data :invoke
  [{:keys [fn args]}]
  {:ast/op :ast.op/invoke
   :ast/fn (tx-data fn)
   :ast/args (indexed-tx-data args)})

(defmethod tx-data :do
  [{:keys [statements ret]}]
  {:ast/op :ast.op/do
   :ast/statements (indexed-tx-data statements)
   :ast/ret (tx-data ret)})

(defmethod tx-data :const
  [{:keys [meta type form]}]
  (cond-> {:ast/op :ast.op/const
           :ast/type type
           :ast/form (pr-str form)}
          meta (assoc :ast/meta (tx-data meta))))

(defmethod tx-data :set
  [{:keys [items]}]
  {:ast/op :ast.op/set
   :ast/items (indexed-tx-data items)})

(defmethod tx-data :static-field
  [{:keys []}]
  {:ast/op :ast.op/static-field})

(defmethod tx-data :local
  [{:keys []}]
  {:ast/op :ast.op/local})

(defmethod tx-data :catch
  [{:keys [local body]}]
  {:ast/op :ast.op/catch
   :ast/local (tx-data local)
   :ast/body (tx-data body)})

(defmethod tx-data :fn-method
  [{:keys [params body]}]
  {:ast/op :ast.op/fn-method
   :ast/params (indexed-tx-data params)
   :ast/body (tx-data body)})

(defmethod tx-data :try
  [{:keys [body catches finally]}]
  (cond-> {:ast/op :ast.op/try
           :ast/body (tx-data body)
           :ast/catches (indexed-tx-data catches)}
          finally (assoc :ast/finally (tx-data finally))))

(defmethod tx-data :if
  [{:keys [test then else]}]
  {:ast/op :ast.op/if
   :ast/test (tx-data test)
   :ast/then (tx-data then)
   :ast/else (tx-data else)})

(defmethod tx-data :def
  [{:keys [meta name init]}]
  (cond-> {:ast/op :ast.op/def
           :ast.def/name (str name)}
          meta (assoc :ast/meta (tx-data meta))
          init (assoc :ast/init (tx-data init))))

(defmethod tx-data :binding
  [{:keys [init]}]
  (cond-> {:ast/op :ast.op/binding}
          init (assoc :ast/init (tx-data init))))

(defmethod tx-data :the-var
  [{:keys []}]
  {:ast/op :ast.op/the-var})

(defmethod tx-data :new
  [{:keys [args]}]
  {:ast/op :ast.op/new
   :ast/args (indexed-tx-data args)})

(defmethod tx-data :instance?
  [{:keys [target]}]
  {:ast/op :ast.op/new
   :ast/target (tx-data target)})

(defmethod tx-data :fn
  [{:keys [local methods]}]
  (cond-> {:ast/op :ast.op/fn
           :ast/methods (indexed-tx-data methods)}
          local (assoc :ast/local (tx-data local))))

(defmethod tx-data :let
  [{:keys [bindings body]}]
  {:ast/op :ast.op/let
   :ast/bindings (indexed-tx-data bindings)
   :ast/body (tx-data body)})

(defmethod tx-data :quote
  [{:keys [expr]}]
  {:ast/op :ast.op/quote
   :ast/expr (tx-data expr)})

(defmethod tx-data :var
  [{:keys []}]
  {:ast/op :ast.op/var})

(defmethod tx-data :loop
  [{:keys [bindings body]}]
  {:ast/op :ast.op/loop
   :ast/bindings (indexed-tx-data bindings)
   :ast/body (tx-data body)})

(defmethod tx-data :throw
  [{:keys [exception]}]
  {:ast/op :ast.op/throw
   :ast/exception (tx-data exception)})

(defn transaction-data 
  "Given a sequence of asts (representing top level forms) returns a sequence of transaction data"
  [asts]
  (map-indexed (fn [i ast]
                 (assoc (tx-data ast)
                   :db/id (d/tempid :db.part/user)
                   :ast/top-level? true
                   :ast/idx i
                   :ast/namespace (-> ast :env :ns str)))
               asts))