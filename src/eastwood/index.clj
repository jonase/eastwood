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
  (doseq [ast (:asts (:analyze-results (analyze-ns ns)))]
    @(d/transact conn [(tx/transaction-data ast)])))

(time (index-ns (db-conn "mydb3") 'clojure.main))

(defn -main [ns]
  (let [ns (read-string ns)]
    (println ns)))

(tx/tx-data (analyze '(def foo) (jvm/empty-env)))

(comment 
  (def conn (db-conn "test"))

  (index-ns conn 'clojure.string)

  (def db (d/db conn))

  (d/q '[:find ?name
         :where
         [?def :ast/op :ast.op/def]
         [?def :ast.def/name ?name]]
       db)



  (reduce (fn [result node]
            (update-in result [(:op node)]
                       (fn [info-map]
                         (update-in  info-map [(:children node)] (fnil inc 0)))))
          {}
          (mapcat ast-nodes (:asts (:analyze-results (analyze-ns 'clojure.string))))))