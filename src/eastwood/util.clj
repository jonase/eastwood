(ns eastwood.util
  (:require [clojure.tools.analyzer.passes :as pass]))


(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))


(defn ast-nodes [ast]
  (lazy-seq
   (cons ast (mapcat ast-nodes (pass/children ast)))))
