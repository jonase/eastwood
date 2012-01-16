(ns eastwood.util
  (:require [clojure.set :as set]))

;; TODO: Profile!

(defn local-bindings [expr]
  (condp = (:op expr)
    :fn-method (if-let [rest-param (:rest-param expr)]
                 (conj (set (:required-params expr)) rest-param)
                 (set (:required-params expr)))
    :let (set (map :local-binding (:binding-inits expr)))
    :let-fn (set (map :local-binding (:binding-inits expr)))
    #{}))
  

(defn free-locals [expr]
  (if (= (:op expr) :local-binding-expr)
    #{(:sym (:local-binding expr))}
    (set/difference (apply set/union (map free-locals
                                          (:children expr))))
                    (local-bindings expr))))


