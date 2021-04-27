(ns testcases.are-true.red-one
  (:require
   [clojure.test :refer [are deftest is join-fixtures testing use-fixtures]]))

(deftest foo
  (are [a b expected] true
    1 2 3))
