(ns testcases.core-async-example.assert
  (:require
   [clojure.core.async :refer [go]]))

(defn sample []
  (go
    (assert false)
    (assert true)))
