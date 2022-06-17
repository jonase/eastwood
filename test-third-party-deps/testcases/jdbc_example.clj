(ns testcases.jdbc-example
  (:require
   [clojure.java.jdbc :refer [with-db-connection]]
   [clojure.test :refer [testing]]))

(defn foo [x y]
  (testing "Foo"
    (with-db-connection [x y]
      42)))
