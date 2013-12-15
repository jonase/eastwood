(ns eastwood.index.schema
  (:require [datomic.api :as d]))

(defn ops []
  (map
   (fn [op]
     {:db/id (d/tempid :db.part/user)
      :db/ident op})
   [:ast.op/binding
    :ast.op/catch
    :ast.op/const
    :ast.op/def
    :ast.op/do
    :ast.op/fn
    :ast.op/fn-method
    :ast.op/host-interop
    :ast.op/if
    :ast.op/import
    :ast.op/instance?
    :ast.op/instance-call
    :ast.op/instance-field
    :ast.op/invoke
    :ast.op/keyword-invoke
    :ast.op/let
    :ast.op/local
    :ast.op/loop
    :ast.op/map
    :ast.op/new
    :ast.op/quote
    :ast.op/recur
    :ast.op/set
    :ast.op/set!
    :ast.op/static-call
    :ast.op/static-field
    :ast.op/the-var
    :ast.op/throw
    :ast.op/try
    :ast.op/var
    :ast.op/vector
    :ast.op/with-meta]))

(defn child-nodes []
  (map (fn [node]
         (assoc node
           :db/id (d/tempid :db.part/db)
           :db/valueType :db.type/ref
           :db/isComponent true
           :db.install/_attribute :db.part/db))
       [;; :catch 
        {:db/ident :ast/local
         :db/cardinality :db.cardinality/one}
        
        ;; :fn-method, :catch, :let :loop :try
        {:db/ident :ast/body
         :db/cardinality :db.cardinality/many}
        
        ;; :const :def :with-meta
        {:db/ident :ast/meta
         :db/cardinality :db.cardinality/one}
        
        ;; :def :binding
        {:db/ident :ast/init
         :db/cardinality :db.cardinality/one}
        
        ;; :do
        {:db/ident :ast/statements
         :db/cardinality :db.cardinality/many}
        
        ;; :do
        {:db/ident :ast/ret
         :db/cardinality :db.cardinality/one}
        
        ;; :fn
        {:db/ident :ast/methods
         :db/cardinality :db.cardinality/many}

        ;; :fn-method
        {:db/ident :ast/params
         :db/cardinality :db.cardinality/many}
        
        ;; :host-interop :instance? :set!
        {:db/ident :ast/target
         :db/cardinality :db.cardinality/one}
        
        ;; :if
        {:db/ident :ast/test
         :db/cardinality :db.cardinality/one}
        
        ;; :if
        {:db/ident :ast/then
         :db/cardinality :db.cardinality/one}
        
        ;; :if
        {:db/ident :ast/else
         :db/cardinality :db.cardinality/one}
        
        ;; :instance-call :instance-field
        {:db/ident :ast/instance
         :db/cardinality :db.cardinality/one}
        
        ;; :instance-call :invoke :keyword-invoke :new
        ;; :static-call
        {:db/ident :ast/args
         :db/cardinality :db.cardinality/many}

        ;; :invoke :keyword-invoke
        {:db/ident :ast/fn
         :db/cardinality :db.cardinality/many}
        
        ;; :let :loop
        {:db/ident :ast/bindings
         :db/cardinality :db.cardinality/many}
        
        ;; :map
        {:db/ident :ast/keys
         :db/cardinality :db.cardinality/many}
        
        ;; :map
        {:db/ident :ast/vals
         :db/cardinality :db.cardinality/many}
        
        ;; :quote :with-meta
        {:db/ident :ast/expr
         :db/cardinality :db.cardinality/one}
        
        ;; :recur
        {:db/ident :ast/exprs
         :db/cardinality :db.cardinality/many}
        
        ;; :set :vector
        {:db/ident :ast/items
         :db/cardinality :db.cardinality/many}
        
        ;; :set!
        {:db/ident :ast/val
         :db/cardinality :db.cardinality/one}
        
        ;; :try
        {:db/ident :ast/catches
         :db/cardinality :db.cardinality/many}
        
        ;; :try
        {:db/ident :ast/finally
         :db/cardinality :db.cardinality/one}

        ;; :throw
        {:db/ident :ast/exception
         :db/cardinality :db.cardinality/one}]))

(defn op-specific []
  (map (fn [node]
         (assoc node
           :db/id (d/tempid :db.part/db)
           :db.install/_attribute :db.part/db))
       [{:db/ident :ast.def/name
         :db/valueType :db.type/string
         :db/cardinality :db.cardinality/one}]))

(defn schema []
  (concat [{:db/id (d/tempid :db.part/db) 
            :db/ident :ast/op
            :db/valueType :db.type/ref
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}
           
           {:db/id (d/tempid :db.part/db)
            :db/ident :ast/idx
            :db/valueType :db.type/long
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}
           
           {:db/id (d/tempid :db.part/db)
            :db/ident :ast/top-level?
            :db/valueType :db.type/boolean
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}

           {:db/id (d/tempid :db.part/db)
            :db/ident :ast/namespace
            :db/valueType :db.type/string
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}

           {:db/id (d/tempid :db.part/db)
            :db/ident :ast/type
            :db/valueType :db.type/keyword
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}

           ;; Forms are strings, created with pr-str
           {:db/id (d/tempid :db.part/db)
            :db/ident :ast/form
            :db/valueType :db.type/string
            :db/cardinality :db.cardinality/one
            :db.install/_attribute :db.part/db}

           ]


          (child-nodes)
          (ops)
          (op-specific)))


