(ns eastwood.index
  (:require [eastwood.util :refer (ast-nodes)]
            [eastwood.analyze-ns :refer (analyze-ns analyze)]
            [eastwood.index.schema :as schema]
            [eastwood.index.tx :as tx]
            [datomic.api :as d]
            [clojure.tools.analyzer.jvm :as jvm]))

(defn db-conn [name]
  (let [uri (str "datomic:mem://" name)
        _ (d/delete-database uri)
        _ (d/create-database uri)
        conn (d/connect uri)]
    @(d/transact conn (schema/schema))
    conn))

(defn index-ns [conn ns]
  (->> ns
       analyze-ns
       :analyze-results
       :asts
       tx/transaction-data
       (d/transact conn)
       deref))

(comment 
  (def conn (db-conn "test"))

  (index-ns conn 'clojure.set)

  (def db (d/db conn))

  ;; This is the misplaced-docstrings linter
  (d/q '[:find ?name
         :where
         [?def :ast.def/name ?name]
         [?def :ast/init ?fn]
         [?fn :ast/methods ?method]
         [?method :ast/body ?body]
         [?body :ast/statements ?statement]
         [?statement :ast/idx 0]
         [?statement :ast/type :string]]
       db)
  ;; Returns #{["bubble-max-key"]} 

)