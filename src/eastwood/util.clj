(ns eastwood.util)

(defn op= [op]
  (fn [ast]
    (= (:op ast) op)))