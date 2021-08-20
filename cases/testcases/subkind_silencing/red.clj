(ns testcases.subkind-silencing.red
  (:require
   [clojure.test :refer [is]]))

(defn foo [x]
  (is (= 42 (x))
      [:not :a :string]))
